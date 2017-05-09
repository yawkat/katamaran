package at.yawk.katamaran

import com.fasterxml.jackson.databind.ObjectMapper
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.element.User
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelModeInfoListEvent
import org.kitteh.irc.client.library.event.channel.RequestedChannelJoinCompleteEvent
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import java.nio.file.Files
import java.nio.file.Paths

/**
 * @author yawkat
 */
fun main(args: Array<String>) {
    val objectMapper = ObjectMapper().findAndRegisterModules()
    val config = Files.newInputStream(Paths.get(args[0])).use { objectMapper.readValue(it, Config::class.java) }
    for (server in config.servers) {
        val client = Client.builder()
                .serverHost(server.host)
                .serverPassword(server.password)
                .serverPort(server.port)
                .secure(server.secure)
                .afterBuildConsumer {
                    it.eventManager.registerEventListener(ServerManager(it, server))
                }
                .build()
        for (channel in server.channels.keys) {
            client.addChannel(channel)
        }
    }
}

private class ServerManager(val client: Client, val config: Server) {
    private fun channelConfig(channel: Channel) = config.channels[channel.name]

    val inviteList = client.serverInfo.getChannelMode('I').get()
    val op = client.serverInfo.getChannelUserMode('o').get()

    @Handler
    fun message(chatMessage: PrivateMessageEvent) {
        val msg = chatMessage.message
        val prefix = client.nick + ": "
        if (msg.startsWith(prefix)) {
            val channel = client.getChannel(chatMessage.target).orElse(null) ?: return
            val channelConfig = channelConfig(channel) ?: return
            val cmd = msg.removePrefix(prefix)
            val reply: String? = when (cmd) {
                "op" -> {
                    if (channelConfig.ops.any { it.matches(chatMessage.actor) }) {
                        channel.commands().mode()
                                .add(true, op, chatMessage.actor.nick)
                                .execute()
                        null
                    } else {
                        "You aren't allowed to do that"
                    }
                }
                else -> "Unknown command"
            }
            if (reply != null) {
                chatMessage.sendReply(reply)
            }
        }
    }

    @Handler
    fun joinComplete(evt: RequestedChannelJoinCompleteEvent) {
        evt.channel.setModeInfoTracking(inviteList, true)
    }

    @Handler
    fun modeInfoReceived(evt: ChannelModeInfoListEvent) {
        val channelConfig = channelConfig(evt.channel) ?: return
        
        if (channelConfig.persistInvite) {
            for (user in evt.channel.users) {
                addUserToChannelInviteList(evt.channel, user)
            }
        }
    }

    @Handler
    fun join(evt: ChannelJoinEvent) {
        val channelConfig = channelConfig(evt.channel) ?: return

        if (channelConfig.persistInvite) {
            addUserToChannelInviteList(evt.channel, evt.user)
        }
    }

    private fun addUserToChannelInviteList(channel: Channel, user: User) {
        if (channel.getModeInfoList(inviteList).get().none {
            UserMask.parse(it.mask.asString()).matches(user)
        }) {
            channel.commands().mode()
                    .add(true, inviteList, UserMask(nick = user.nick, user = null, host = user.host).toString())
                    .execute()
        }
    }
}
