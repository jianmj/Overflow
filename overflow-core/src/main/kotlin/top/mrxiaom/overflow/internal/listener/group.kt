package top.mrxiaom.overflow.internal.listener

import cn.evole.onebot.sdk.event.message.GroupMessageEvent
import cn.evole.onebot.sdk.event.message.GroupMessageEvent.GroupSender
import cn.evole.onebot.sdk.event.notice.group.GroupMsgDeleteNoticeEvent
import cn.evole.onebot.sdk.event.notice.group.GroupNotifyNoticeEvent
import cn.evole.onebot.sdk.response.group.GroupMemberInfoResp
import cn.evolvefield.onebot.client.listener.EventListener
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.MemberPermission
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.event.events.NudgeEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.MiraiInternalApi
import top.mrxiaom.overflow.internal.asOnebot
import top.mrxiaom.overflow.internal.contact.BotWrapper
import top.mrxiaom.overflow.internal.contact.GroupWrapper
import top.mrxiaom.overflow.internal.contact.MemberWrapper
import top.mrxiaom.overflow.internal.message.OnebotMessages

internal class GroupMessageListener(
    val bot: BotWrapper
) : EventListener<GroupMessageEvent> {
    @OptIn(MiraiInternalApi::class)
    override suspend fun onMessage(e: GroupMessageEvent) {
        when(e.subType) {
            "normal" -> {
                val group = bot.group(e.groupId)
                val member = e.sender.wrapAsMember(group)

                var miraiMessage = OnebotMessages.deserializeFromOneBot(bot, e.message)
                val messageString = miraiMessage.toString()
                val messageSource = object : OnlineMessageSource.Incoming.FromGroup() {
                    override val bot: Bot = this@GroupMessageListener.bot
                    override val ids: IntArray = arrayOf(e.messageId).toIntArray()
                    override val internalIds: IntArray = ids
                    override val isOriginalMessageInitialized: Boolean = true
                    override val originalMessage: MessageChain = miraiMessage
                    override val sender: Member = member
                    override val time: Int = e.time.toInt()
                }
                miraiMessage = messageSource.plus(miraiMessage)
                if (member.id == bot.id) {
                    // TODO: 过滤自己发送的消息
                } else {
                    bot.logger.verbose("[${group.name}(${group.id})] ${member.nameCardOrNick}(${member.id}) -> $messageString")
                    net.mamoe.mirai.event.events.GroupMessageEvent(
                        member.nameCardOrNick, when (e.sender.role) {
                            "owner" -> MemberPermission.OWNER
                            "admin" -> MemberPermission.ADMINISTRATOR
                            else -> MemberPermission.MEMBER
                        }, member, miraiMessage, e.time.toInt()
                    ).broadcast()
                }
            }
            "anonymous" -> {
                TODO("匿名消息")
            }
            "notice" -> {
                TODO("系统提示，如 管理员已禁止群内匿名聊天")
            }
        }
    }
}

internal class GroupNotifyListener(
    val bot: BotWrapper
) : EventListener<GroupNotifyNoticeEvent> {
    @OptIn(MiraiInternalApi::class)
    override suspend fun onMessage(e: GroupNotifyNoticeEvent) {
        val group = bot.group(e.groupId)
        when (e.subType) {
            "poke" -> {
                val operator = group.members[e.operatorId] ?: throw IllegalStateException("群 ${group.id} 戳一戳 无法获取操作者")
                val target = group.members[e.targetId] ?: throw IllegalStateException("群 ${group.id} 戳一戳 无法获取目标")
                // TODO: 戳一戳无法获取被戳一方的动作、后缀信息
                NudgeEvent(operator, target, group, "拍了拍", "").broadcast()
            }
        }
    }
}

internal class GroupMessageRecallListener(
    val bot: BotWrapper
): EventListener<GroupMsgDeleteNoticeEvent> {
    @OptIn(MiraiInternalApi::class)
    override suspend fun onMessage(e: GroupMsgDeleteNoticeEvent) {
        val group = bot.group(e.groupId)
        val operator = group.members[e.operatorId]
        MessageRecallEvent.GroupRecall(bot,
            bot.id, // TODO: Onebot 无法获取被撤回消息的发送者
            intArrayOf(e.msgId.toInt()),
            intArrayOf(e.msgId.toInt()),
            (e.time / 1000).toInt(),
            operator, group,
            group.botAsMember // TODO: Onebot 无法获取被撤回消息的发送者
        )
    }
}

fun GroupSender.wrapAsMember(group: Group): MemberWrapper {
    return (group as GroupWrapper).updateMember(
        MemberWrapper(group.bot.asOnebot, group, GroupMemberInfoResp().also {
            it.groupId = group.id
            it.userId = userId.toLong()
            it.nickname = nickname
            it.card = card ?: ""
            it.sex = sex ?: ""
            it.age = age ?: 0
            it.area = area ?: ""
            it.level = level?.toIntOrNull() ?: 0
            it.role = role ?: "member"
            it.title = title ?: ""
        })
    )
}

private suspend fun BotWrapper.group(groupId: Long): Group {
    return getGroup(groupId) ?: kotlin.run {
        val data = impl.getGroupInfo(groupId, false).data ?: throw IllegalStateException("无法取得群信息")
        updateGroup(GroupWrapper(this, data))
    }
}