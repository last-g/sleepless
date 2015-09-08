package sleepless.gather.sources.vk_api


trait VkApiMethod {
    def name: String
    def params: Map[String, String]
}

trait Groupable {
    self: VkApiMethod =>

    def canBeGrouped: Boolean
}