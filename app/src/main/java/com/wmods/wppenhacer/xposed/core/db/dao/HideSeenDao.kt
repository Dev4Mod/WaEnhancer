package com.wmods.wppenhacer.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wmods.wppenhacer.xposed.core.db.entity.HideSeenEntity

@Dao
interface HideSeenDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(entity: HideSeenEntity): Long

    @Query("UPDATE hide_seen_messages SET read = :viewed WHERE (jid IN (:jids) OR userjid IN (:jids)) AND message_id = :messageId")
    fun updateRead(jids: List<String>, messageId: String, viewed: Int): Int

    @Query("UPDATE hide_seen_messages SET played = :viewed WHERE (jid IN (:jids) OR userjid IN (:jids)) AND message_id = :messageId")
    fun updatePlayed(jids: List<String>, messageId: String, viewed: Int): Int

    @Query("UPDATE hide_seen_messages SET read = :viewed WHERE (jid IN (:jids) OR userjid IN (:jids)) AND message_id IN (:messageIds)")
    fun updateReadBatch(jids: List<String>, messageIds: List<String>, viewed: Int): Int

    @Query("UPDATE hide_seen_messages SET played = :viewed WHERE (jid IN (:jids) OR userjid IN (:jids)) AND message_id IN (:messageIds)")
    fun updatePlayedBatch(jids: List<String>, messageIds: List<String>, viewed: Int): Int

    @Query("SELECT read FROM hide_seen_messages WHERE (jid IN (:jids) OR userjid IN (:jids)) AND message_id = :messageId")
    fun getReadState(jids: List<String>, messageId: String): Int?

    @Query("SELECT played FROM hide_seen_messages WHERE (jid IN (:jids) OR userjid IN (:jids)) AND message_id = :messageId")
    fun getPlayedState(jids: List<String>, messageId: String): Int?

    @Query("SELECT _id, jid, userjid, message_id, read, played FROM hide_seen_messages WHERE (jid IN (:jids) OR userjid IN (:jids)) AND read = :viewed ORDER BY _id ASC")
    fun getMessagesByReadState(jids: List<String>, viewed: Int): List<HideSeenEntity>

    @Query("SELECT _id, jid, userjid, message_id, read, played FROM hide_seen_messages WHERE (jid IN (:jids) OR userjid IN (:jids)) AND played = :viewed ORDER BY _id ASC")
    fun getMessagesByPlayedState(jids: List<String>, viewed: Int): List<HideSeenEntity>

    @Query("UPDATE hide_seen_messages SET userjid = :userJid WHERE jid = :jid AND message_id = :messageId AND userjid IS NULL")
    fun updateUserJidIfNeeded(jid: String, userJid: String, messageId: String): Int
}
