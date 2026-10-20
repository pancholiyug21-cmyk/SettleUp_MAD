package com.example.splitwsie.data.repository

import com.example.splitwsie.data.local.dao.ExpenseDao
import com.example.splitwsie.data.local.dao.GroupDao
import com.example.splitwsie.data.local.dao.GroupMemberRow
import com.example.splitwsie.data.local.dao.SettlementDao
import com.example.splitwsie.data.local.entity.GroupEntity
import com.example.splitwsie.data.local.entity.MemberEntity
import com.example.splitwsie.domain.model.Group
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.repository.GroupRepository
import com.example.splitwsie.data.remote.model.FirestoreGroup
import com.example.splitwsie.data.remote.model.FirestoreGroupMember
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

import java.util.Date
import java.util.UUID

class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val expenseDao: ExpenseDao,
    private val settlementDao: SettlementDao,
    private val firestore: FirebaseFirestore
) : GroupRepository {

    override fun getAllGroups(): Flow<List<Group>> =
        groupDao.getAllGroups().map { it.withMembers() }

    override fun getGroupsForUser(userId: String): Flow<List<Group>> = callbackFlow {
        val roomJob = launch {
            groupDao.getGroupsForUser(userId).map { it.withMembers() }.collect {
                send(it)
            }
        }

        val listener = firestore.collection("groups")
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    launch {
                        snapshot.documentChanges.forEach { change ->
                            val doc = change.document

                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                groupDao.deleteGroup(doc.id)
                                groupDao.deleteMembersOfGroup(doc.id)
                                return@forEach
                            }

                            val firestoreGroup = doc.toObject(FirestoreGroup::class.java)
                            val entity = GroupEntity(
                                id = firestoreGroup.groupId.ifEmpty { doc.id },
                                name = firestoreGroup.name,
                                description = firestoreGroup.description,
                                currency = firestoreGroup.currency,
                                inviteCode = firestoreGroup.inviteCode,
                                createdByUserId = firestoreGroup.createdByUserId,
                                category = firestoreGroup.category,
                                iconUrl = firestoreGroup.iconUrl,
                                simplifyDebts = firestoreGroup.simplifyDebts
                            )
                            groupDao.insertGroup(entity)

                            firestoreGroup.memberIds.forEach { memberId ->
                                groupDao.insertMember(MemberEntity(groupId = entity.id, userId = memberId))
                            }
                        }
                    }
                }
            }

        awaitClose {
            listener.remove()
            roomJob.cancel()
        }
    }

    /** Resolves members for a whole page of groups in one query rather than one per group. */
    private suspend fun List<GroupEntity>.withMembers(): List<Group> {
        if (isEmpty()) return emptyList()
        val membersByGroup = groupDao.getMembersOfGroups(map { it.id })
            .groupBy { it.groupId }
        return map { entity -> entity.toDomain(membersByGroup[entity.id].orEmpty().map { it.toUser() }) }
    }

    override suspend fun getGroupById(groupId: String): Group? {
        val entity = groupDao.getGroupById(groupId) ?: return null
        val members = groupDao.getMembersOfGroup(groupId).map { u ->
            User(
                id = u.id,
                name = u.name,
                email = u.email,
                phoneNumber = u.phoneNumber,
                upiId = u.upiId,
                bankAccount = u.bankAccount,
                defaultCurrency = u.defaultCurrency
            )
        }
        return entity.toDomain(members)
    }

    override suspend fun getGroupByInviteCode(inviteCode: String): Group? {
        val entity = groupDao.getGroupByInviteCode(inviteCode.trim().uppercase()) ?: return null
        return getGroupById(entity.id)
    }

    override suspend fun createGroup(group: Group) {
        groupDao.insertGroup(group.toEntity())

        group.members.forEach { member ->
            groupDao.insertMember(MemberEntity(groupId = group.id, userId = member.id))
        }

        // Sync to Firestore
        val firestoreGroup = FirestoreGroup(
            groupId = group.id,
            name = group.name,
            description = group.description,
            currency = group.currency,
            inviteCode = group.inviteCode,
            createdByUserId = group.createdByUserId ?: "",
            category = group.category,
            iconUrl = group.iconUrl,
            simplifyDebts = group.simplifyDebts,
            memberIds = group.members.map { it.id }
        )
        firestore.collection("groups").document(group.id).set(firestoreGroup)
            .addOnFailureListener {
                // Offline fallback - handled by Firestore offline persistence
            }

        group.members.forEach { member ->
            val firestoreMember = FirestoreGroupMember(
                uid = member.id,
                role = if (member.id == group.createdByUserId) "OWNER" else "MEMBER",
                displayName = member.name
            )
            firestore.collection("groups").document(group.id).collection("members").document(member.id).set(firestoreMember)
                .addOnFailureListener {}
        }
    }

    override suspend fun updateGroup(group: Group) {
        val existing = groupDao.getGroupById(group.id) ?: return
        groupDao.updateGroup(
            existing.copy(
                name = group.name,
                description = group.description,
                currency = group.currency,
                inviteCode = group.inviteCode,
                category = group.category,
                iconUrl = group.iconUrl,
                simplifyDebts = group.simplifyDebts
            )
        )

        val updateData = mapOf(
            "name" to group.name,
            "description" to group.description,
            "currency" to group.currency,
            "category" to group.category,
            "simplifyDebts" to group.simplifyDebts
        )
        firestore.collection("groups").document(group.id).update(updateData)
            .addOnFailureListener {}
    }

    override suspend fun addMemberToGroup(groupId: String, userId: String) {
        groupDao.insertMember(MemberEntity(groupId = groupId, userId = userId))

        // Use arrayUnion to add member id safely
        firestore.collection("groups").document(groupId)
            .update("memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            .addOnFailureListener {}
    }

    override suspend fun removeMemberFromGroup(groupId: String, userId: String) {
        groupDao.deleteMember(groupId, userId)

        firestore.collection("groups").document(groupId)
            .update("memberIds", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
            .addOnFailureListener {}
    }

    override suspend fun isMember(groupId: String, userId: String): Boolean =
        groupDao.countMembership(groupId, userId) > 0

    /**
     * Deletes the group and everything that belonged to it.
     *
     * Expenses, splits and settlements were previously left behind, so their rows stayed in the
     * database forever and still contributed to cross-group balance totals on the home screen.
     */
    override suspend fun deleteGroup(groupId: String) {
        expenseDao.deleteSplitsForGroup(groupId)
        expenseDao.deleteExpensesForGroup(groupId)
        settlementDao.deleteForGroup(groupId)
        groupDao.deleteMembersOfGroup(groupId)
        groupDao.deleteGroup(groupId)

        firestore.collection("groups").document(groupId).delete()
            .addOnFailureListener {}
    }

    private fun GroupMemberRow.toUser() = User(
        id = id,
        name = name,
        email = email,
        phoneNumber = phoneNumber,
        upiId = upiId,
        bankAccount = bankAccount,
        defaultCurrency = defaultCurrency
    )

    private fun GroupEntity.toDomain(members: List<User>) = Group(
        id = id,
        name = name,
        description = description,
        currency = currency,
        members = members,
        inviteCode = inviteCode,
        createdByUserId = createdByUserId,
        category = category,
        iconUrl = iconUrl,
        simplifyDebts = simplifyDebts
    )

    private fun Group.toEntity() = GroupEntity(
        id = id,
        name = name,
        description = description,
        currency = currency,
        inviteCode = inviteCode,
        createdByUserId = createdByUserId ?: members.firstOrNull()?.id.orEmpty(),
        category = category,
        iconUrl = iconUrl,
        simplifyDebts = simplifyDebts
    )
}
