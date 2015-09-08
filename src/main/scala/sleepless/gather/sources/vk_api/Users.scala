package sleepless.gather.sources.vk_api


object Users {

    case class Get(userId: Long,
                   fields: Set[String] = Set.empty,
                   nameCase: String = "nom",
                   canBeGrouped: Boolean = true) extends VkApiMethod with Groupable {
        val name: String = "users.get"
        val params: Map[String, String] = Map(
            "user_ids" -> userId.toString,
            "fields" -> fields.mkString(","),
            "name_case" -> nameCase
        )

    }

    case class GetSubscriptions(userId: Long,
                                offset: Int = 0,
                                count: Int = 20,
                                fields: Set[String]) extends VkApiMethod {
        val name = "users.getSubscriptions"
        val params = Map(
            "user_id" -> userId.toString,
            "extended" -> 1.toString,
            "offset" -> offset.toString,
            "count" -> count.toString,
            "fields" -> fields.mkString(",")
        )
    }


}



