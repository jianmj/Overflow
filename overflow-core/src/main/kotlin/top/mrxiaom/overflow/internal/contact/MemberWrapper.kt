@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package top.mrxiaom.overflow.internal.contact

import cn.evole.onebot.sdk.response.group.GroupMemberInfoResp
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.contact.active.MemberActive
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.action.MemberNudge
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.MiraiInternalApi
import net.mamoe.mirai.utils.currentTimeSeconds
import top.mrxiaom.overflow.contact.Updatable
import top.mrxiaom.overflow.internal.Overflow
import top.mrxiaom.overflow.internal.message.OnebotMessages
import top.mrxiaom.overflow.internal.message.OnebotMessages.findForwardMessage
import top.mrxiaom.overflow.internal.message.data.WrappedVideo
import top.mrxiaom.overflow.internal.utils.safeMessageIds
import top.mrxiaom.overflow.spi.FileService
import kotlin.coroutines.CoroutineContext

@OptIn(MiraiInternalApi::class)
class MemberWrapper(
    val botWrapper: BotWrapper,
    val groupWrapper: GroupWrapper,
    internal var impl: GroupMemberInfoResp
) : NormalMember, Updatable {

    val data: GroupMemberInfoResp
        get() = impl
    override suspend fun queryUpdate() {
        setImpl(botWrapper.impl.getGroupMemberInfo(impl.groupId, impl.userId, false).data)
    }
    
    fun setImpl(impl: GroupMemberInfoResp) {
        if (impl.card != impl.card) {
            botWrapper.eventDispatcher.broadcastAsync(
                MemberCardChangeEvent(this.impl.card, impl.card, this)
            )
        }
        this.impl = impl
    }

    override val active: MemberActive
        get() = TODO("Not yet implemented")
    override val bot: Bot = botWrapper
    override val group: Group = groupWrapper
    override val id: Long = impl.userId
    override val joinTimestamp: Int
        get() = impl.joinTime
    override val lastSpeakTimestamp: Int
        get() = impl.lastSentTime
    var _muteTimestamp: Int = 0 // TODO: Onebot 无法获取禁言剩余时间
    override val muteTimeRemaining: Int
        get() = if (_muteTimestamp == 0 || _muteTimestamp == 0xFFFFFFFF.toInt()) {
            0
        } else {
            (_muteTimestamp - currentTimeSeconds().toInt()).coerceAtLeast(0)
        }
    override val coroutineContext: CoroutineContext = CoroutineName("((Bot/${bot.id})Group/${group.id})Member/$id")
    override var nameCard: String
        get() = impl.card ?: ""
        set(value) {
            if (id != bot.id) {
                group.checkBotPermission(MemberPermission.ADMINISTRATOR)
            }
            Overflow.instance.launch {
                botWrapper.impl.setGroupCard(impl.groupId, id, value)
            }
        }
    override val nick: String = impl.nickname
    override val permission: MemberPermission = when(impl.role) {
        "owner" -> MemberPermission.OWNER
        "admin" -> MemberPermission.ADMINISTRATOR
        else -> MemberPermission.MEMBER
    }
    override val remark: String
        get() = botWrapper.friends[id]?.remark ?: ""
    override var specialTitle: String
        get() = impl.title
        set(value) {
            Overflow.instance.launch {
                botWrapper.impl.setGroupSpecialTitle(impl.groupId, id, value, -1)
            }
        }
    override suspend fun kick(message: String, block: Boolean) {
        botWrapper.impl.setGroupKick(impl.groupId, id, block)
    }

    override suspend fun modifyAdmin(operation: Boolean) {
        botWrapper.impl.setGroupAdmin(impl.groupId, id, operation)
    }

    override suspend fun mute(durationSeconds: Int) {
        botWrapper.impl.setGroupBan(group.id, id, durationSeconds)
    }

    override fun nudge(): MemberNudge {
        return MemberNudge(this)
    }

    override suspend fun sendMessage(message: String): MessageReceipt<NormalMember> {
        return sendMessage(PlainText(message))
    }

    @OptIn(MiraiInternalApi::class)
    override suspend fun sendMessage(message: Message): MessageReceipt<NormalMember> {
        if (GroupTempMessagePreSendEvent(this, message).broadcast().isCancelled)
            throw EventCancelledException("消息发送已被取消")

        val messageChain = message.toMessageChain()
        var throwable: Throwable? = null
        val receipt = kotlin.runCatching {
            val forward = message.findForwardMessage()
            val messageIds = if (forward != null) {
                val nodes = OnebotMessages.serializeForwardNodes(forward.nodeList)
                val response = botWrapper.impl.sendPrivateForwardMsg(id, nodes)
                response.data.safeMessageIds
            } else {
                val msg = OnebotMessages.serializeToOneBotJson(message)
                val response = botWrapper.impl.sendPrivateMsg(id, msg, false)
                response.data.safeMessageIds
            }
            @Suppress("DEPRECATION_ERROR")
            MessageReceipt(object : OnlineMessageSource.Outgoing.ToTemp() {
                override val bot: Bot = this@MemberWrapper.bot
                override val ids: IntArray = messageIds
                override val internalIds: IntArray = ids
                override val isOriginalMessageInitialized: Boolean = true
                override val originalMessage: MessageChain = message.toMessageChain()
                override val sender: Bot = bot
                override val target: Member = this@MemberWrapper
                override val time: Int = currentTimeSeconds().toInt()
            }, this)
        }.onFailure { throwable = it }.getOrNull()
        GroupTempMessagePostSendEvent(this, messageChain, throwable, receipt).broadcast()

        return receipt ?: throw throwable!!
    }

    override suspend fun unmute() {
        mute(0)
    }

    override suspend fun uploadImage(resource: ExternalResource): Image {
        return OnebotMessages.imageFromFile(FileService.instance!!.upload(resource))
    }

    override suspend fun uploadShortVideo(
        thumbnail: ExternalResource,
        video: ExternalResource,
        fileName: String?
    ): ShortVideo {
        return OnebotMessages.videoFromFile(FileService.instance!!.upload(video))
    }
}