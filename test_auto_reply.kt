import com.jarvis.messaging.AutoReplyMode
import com.jarvis.messaging.AutoReplyPolicyManager

fun main() {
    AutoReplyPolicyManager.setMode(AutoReplyMode.AI_SMART_REPLY)
    println(AutoReplyPolicyManager.mode)
}
