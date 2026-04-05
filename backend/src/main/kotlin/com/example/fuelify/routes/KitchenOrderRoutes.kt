package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object KitchenOrders : Table("kitchen_order") {
    val orderId    = integer("order_id").autoIncrement()
    val userId     = integer("user_id")
    val planType   = varchar("plan_type", 20)
    val portionSize= varchar("portion_size", 20)
    val spiceLevel = varchar("spice_level", 20)
    val orderDate  = datetime("order_date")
    val status     = varchar("status", 30).default("preparing")
    val totalPrice = double("total_price").default(0.0)
    val notes      = text("notes").default("")
    override val primaryKey = PrimaryKey(orderId)
}

@Serializable
data class KitchenOrderRequest(
    val plan_type:    String,
    val portion_size: String,
    val spice_level:  String,
    val notes:        String = ""
)

@Serializable
data class KitchenOrderResponse(
    val order_id:    Int,
    val status:      String,
    val total_price: Double,
    val message:     String
)

fun Route.kitchenOrderRoutes() {

    post("/users/{id}/kitchen-order") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null))

        val req = call.receive<KitchenOrderRequest>()

        // Price logic: daily=29.99, weekly=179.99, large+20%, small-10%
        val base  = if (req.plan_type == "weekly") 179.99 else 29.99
        val price = when (req.portion_size) {
            "large" -> base * 1.20
            "small" -> base * 0.90
            else    -> base
        }

        val orderId = dbQuery {
            KitchenOrders.insert {
                it[KitchenOrders.userId]     = userId
                it[KitchenOrders.planType]   = req.plan_type
                it[KitchenOrders.portionSize]= req.portion_size
                it[KitchenOrders.spiceLevel] = req.spice_level
                it[KitchenOrders.orderDate]  = LocalDateTime.now()
                it[KitchenOrders.status]     = "preparing"
                it[KitchenOrders.totalPrice] = Math.round(price * 100.0) / 100.0
                it[KitchenOrders.notes]      = req.notes
            } get KitchenOrders.orderId
        }

        call.respond(ApiResponse(success = true, message = "Order placed!", data = KitchenOrderResponse(
            order_id    = orderId,
            status      = "preparing",
            total_price = Math.round(price * 100.0) / 100.0,
            message     = "Your meals are being prepared"
        )))
    }
}
