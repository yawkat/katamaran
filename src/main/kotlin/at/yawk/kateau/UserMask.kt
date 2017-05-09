package at.yawk.kateau

import com.fasterxml.jackson.annotation.JsonCreator
import org.kitteh.irc.client.library.element.User
import java.util.regex.Pattern

data class UserMask(
        val nick: String?,
        val user: String?,
        val host: String?
) {
    companion object {
        private val pattern = Pattern.compile("([^!@*]+|\\*)!([^!@*]+|\\*)@([^!@*]+|\\*)")

        @JvmStatic
        @JsonCreator
        fun parse(value: String): UserMask {
            val matcher = pattern.matcher(value)
            if (!matcher.matches()) throw IllegalArgumentException("Cannot parse user mask '$value'")

            fun wildcard(s: String) = if (s == "*") null else s

            return UserMask(
                    nick = wildcard(matcher.group(1)),
                    user = wildcard(matcher.group(2)),
                    host = wildcard(matcher.group(3))
            )
        }
    }

    fun matches(user: User) =
            (nick == null || user.nick == nick) &&
                    (this.user == null || user.userString == this.user) &&
                    (host == null || user.host == host)

    override fun toString(): String {
        return "${nick ?: '*'}!${user ?: '*'}@${host ?: '*'}"
    }
}