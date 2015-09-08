package sleepless.gather.models.vk


sealed trait User extends VkObject {
    def id: Long
}

case class ActiveUser(id: Long,
                      first_name: String,
                      last_name: String,
                      last_seen: Option[LastSeen]) extends User

case class DeactivatedUser(id: Long) extends User


case class LastSeen(time: Long, platform: Option[Int])
