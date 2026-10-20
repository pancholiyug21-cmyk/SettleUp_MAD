package com.example.splitwsie.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.splitwsie.data.local.dao.ExpenseDao
import com.example.splitwsie.data.local.dao.GroupDao
import com.example.splitwsie.data.local.dao.SettlementDao
import com.example.splitwsie.data.local.dao.UserDao
import com.example.splitwsie.data.local.entity.ExpenseEntity
import com.example.splitwsie.data.local.entity.GroupEntity
import com.example.splitwsie.data.local.entity.MemberEntity
import com.example.splitwsie.data.local.entity.SettlementEntity
import com.example.splitwsie.data.local.entity.SplitEntity
import com.example.splitwsie.data.local.entity.UserEntity
import com.example.splitwsie.data.local.entity.FriendEntity
import com.example.splitwsie.data.local.dao.FriendDao
import com.example.splitwsie.data.local.entity.CommentEntity
import com.example.splitwsie.data.local.dao.CommentDao

@Database(
    entities = [
        GroupEntity::class,
        UserEntity::class,
        MemberEntity::class,
        ExpenseEntity::class,
        SplitEntity::class,
        SettlementEntity::class,
        FriendEntity::class,
        CommentEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SettleDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun userDao(): UserDao
    abstract fun settlementDao(): SettlementDao
    abstract fun friendDao(): FriendDao
    abstract fun commentDao(): CommentDao

    companion object {
        const val DATABASE_NAME = "settle_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN upiId TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN bankAccount TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN defaultCurrency TEXT NOT NULL DEFAULT 'INR'")
            }
        }

        /**
         * Adds settlement persistence. Before this version payments were never recorded at all,
         * so a "completed" payment left the balance untouched.
         *
         * Also adds the lookup indices the expense/split joins were missing, which previously
         * meant a full table scan for every group's expense list.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `settlements` (
                        `id` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `fromUserId` TEXT NOT NULL,
                        `toUserId` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `date` INTEGER NOT NULL,
                        `method` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `providerTxnId` TEXT,
                        `providerRefId` TEXT,
                        `expenseId` TEXT,
                        `note` TEXT,
                        `verificationNotes` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_settlements_idempotencyKey` " +
                        "ON `settlements` (`idempotencyKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_settlements_groupId` ON `settlements` (`groupId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_settlements_fromUserId` ON `settlements` (`fromUserId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_settlements_toUserId` ON `settlements` (`toUserId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expenses_groupId` ON `expenses` (`groupId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_splits_expenseId` ON `splits` (`expenseId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_group_members_userId` ON `group_members` (`userId`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `friends` (
                        `requesterId` TEXT NOT NULL,
                        `receiverId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`requesterId`, `receiverId`),
                        FOREIGN KEY(`requesterId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`receiverId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_friends_requesterId` ON `friends` (`requesterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_friends_receiverId` ON `friends` (`receiverId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN category TEXT NOT NULL DEFAULT 'Other'")
                db.execSQL("ALTER TABLE groups ADD COLUMN iconUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE splits ADD COLUMN shares REAL DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses_new` (
                        `id` TEXT NOT NULL,
                        `groupId` TEXT,
                        `title` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `paidById` TEXT NOT NULL,
                        `notes` TEXT,
                        `receiptImageUrl` TEXT,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `expenses_new` (`id`, `groupId`, `title`, `amount`, `category`, `date`, `paidById`, `notes`, `status`)
                    SELECT `id`, `groupId`, `title`, `amount`, `category`, `date`, `paidById`, `notes`, `status` FROM `expenses`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `expenses`")
                db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_groupId` ON `expenses` (`groupId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN simplifyDebts INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `comments` (
                        `id` TEXT NOT NULL,
                        `expenseId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_expenseId` ON `comments` (`expenseId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `activities` (
                        `id` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `targetUserId` TEXT,
                        `groupId` TEXT,
                        `expenseId` TEXT,
                        `settlementId` TEXT,
                        `amount` REAL,
                        `title` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_groupId` ON `activities` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_userId` ON `activities` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_timestamp` ON `activities` (`timestamp`)")

                // Backfill expenses
                db.execSQL("""
                    INSERT INTO `activities` (`id`, `timestamp`, `type`, `userId`, `groupId`, `expenseId`, `amount`, `title`)
                    SELECT 'act_exp_' || `id`, `date`, 'EXPENSE_ADDED', `paidById`, `groupId`, `id`, `amount`, `title`
                    FROM `expenses`
                """.trimIndent())

                // Backfill settlements
                db.execSQL("""
                    INSERT INTO `activities` (`id`, `timestamp`, `type`, `userId`, `targetUserId`, `groupId`, `settlementId`, `amount`)
                    SELECT 'act_set_' || `id`, `date`, 'SETTLED', `fromUserId`, `toUserId`, `groupId`, `id`, `amount`
                    FROM `settlements`
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN repeatInterval TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN nextRepeatDate INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settlements ADD COLUMN paymentId TEXT DEFAULT NULL")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12
        )
    }
}
