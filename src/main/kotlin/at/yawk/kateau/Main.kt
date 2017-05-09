package at.yawk.kateau

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.element.User
import org.kitteh.irc.client.library.element.mode.ModeInfo
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.channel.ChannelModeInfoListEvent
import org.kitteh.irc.client.library.event.channel.RequestedChannelJoinCompleteEvent
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import org.kitteh.irc.client.library.util.Sanity
import java.nio.file.Files
import java.nio.file.Paths

/**
 * @author yawkat
 */
fun main(args: Array<String>) {
    val objectMapper = ObjectMapper(YAMLFactory()).findAndRegisterModules()
    val config = Files.newInputStream(Paths.get(args[0])).use { objectMapper.readValue(it, Config::class.java) }
    for (server in config.servers) {
        val client = Client.builder()
                .serverHost(server.host)
                .serverPassword(server.password)
                .serverPort(server.port)
                .secure(server.secure)
                .afterBuildConsumer {
                    it.setInputListener(::println)
                    it.eventManager.registerEventListener(ServerManager(it, server))
                }
                .build()
        for (channel in server.channels.keys) {
            client.addChannel(channel)
        }
    }
}

class ServerManager(val client: Client, val config: Server) {
    private fun channelConfig(channel: Channel) = config.channels[channel.name]

    val inviteList by lazy { client.serverInfo.getChannelMode('I').get() }
    val op by lazy { client.serverInfo.getChannelUserMode('o').get() }

    @Handler
    fun message(evt: ChannelMessageEvent) {
        val msg = evt.message
        val prefix = client.nick + ": "
        if (msg.startsWith(prefix)) {
            val channelConfig = channelConfig(evt.channel) ?: return
            val cmd = msg.removePrefix(prefix)
            val reply: String? = when {
                cmd == "op" -> {
                    if (channelConfig.ops.any { it.matches(evt.actor) }) {
                        evt.channel.commands().mode()
                                .add(true, op, evt.actor.nick)
                                .execute()
                        null
                    } else {
                        "You aren't allowed to do that"
                    }
                }
                cmd.startsWith("invite ") -> {
                    if (channelConfig.inviters.any { it.matches(evt.actor) }) {
                        val invitee = cmd.removePrefix("invite ")
                        Sanity.safeMessageCheck(invitee, "Invitee")
                        Sanity.truthiness(invitee.indexOf(' ') == -1, "Invitee cannot have spaces")
                        client.sendRawLine("INVITE $invitee ${evt.channel.messagingName}")
                        "Invite sent"
                    } else {
                        "You aren't allowed to do that"
                    }
                }
                else -> "Unknown command"
            }
            if (reply != null) {
                evt.sendReply(reply)
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
        
        if (channelConfig.persistInvite && evt.mode == inviteList) {
            for (user in evt.channel.users) {
                addUserToChannelInviteList(evt.channel, user, evt.modeInfo)
            }
        }
    }

    @Handler
    fun join(evt: ChannelJoinEvent) {
        val channelConfig = channelConfig(evt.channel) ?: return

        if (channelConfig.persistInvite) {
            addUserToChannelInviteList(evt.channel, evt.user,
                    evt.channel.getModeInfoList(inviteList).orElse(null) ?: return)
        }
    }

    private fun addUserToChannelInviteList(channel: Channel, user: User, list: List<ModeInfo>) {
        if (list.none { UserMask.parse(it.mask.asString()).matches(user) }) {
            channel.commands().mode()
                    .add(true, inviteList, UserMask(nick = user.nick, user = null, host = user.host).toString())
                    .execute()
        }
    }
}
