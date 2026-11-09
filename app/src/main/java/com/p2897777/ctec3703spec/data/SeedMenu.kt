package com.p2897777.ctec3703spec.data

import com.p2897777.ctec3703spec.model.Category
import com.p2897777.ctec3703spec.model.Product

/** Starting menu inserted when the database is first created. */
object SeedMenu {
    val items: List<Product> = listOf(
        p("Flat White", "Double ristretto with silky steamed milk.", Category.HOT_DRINKS, 340, "☕", "hot", "coffee", "milk"),
        p("Cappuccino", "Espresso, steamed milk and a thick layer of foam.", Category.HOT_DRINKS, 320, "☕", "hot", "coffee", "milk"),
        p("Americano", "Espresso topped up with hot water.", Category.HOT_DRINKS, 280, "☕", "hot", "coffee"),
        p("Chai Latte", "Spiced black tea with steamed milk.", Category.HOT_DRINKS, 350, "🍵", "hot", "tea", "milk", "spiced"),
        p("Hot Chocolate", "Rich Belgian chocolate with whipped cream.", Category.HOT_DRINKS, 360, "🍫", "hot", "sweet", "milk"),
        p("English Breakfast Tea", "Classic black tea, served with milk on the side.", Category.HOT_DRINKS, 220, "🫖", "hot", "tea"),
        p("Iced Latte", "Espresso over ice with cold milk.", Category.COLD_DRINKS, 370, "🧊", "cold", "coffee", "milk"),
        p("Iced Caramel Frappé", "Blended coffee, caramel and ice.", Category.COLD_DRINKS, 420, "🥤", "cold", "coffee", "sweet"),
        p("Fresh Orange Juice", "Freshly squeezed oranges.", Category.COLD_DRINKS, 300, "🍊", "cold", "fruit"),
        p("Strawberry Smoothie", "Strawberries, banana and yoghurt.", Category.COLD_DRINKS, 390, "🍓", "cold", "fruit", "sweet"),
        p("Victoria Sponge", "Jam and buttercream between two light sponges.", Category.CAKES, 330, "🍰", "sweet", "cake"),
        p("Chocolate Fudge Cake", "Dense chocolate sponge with fudge icing.", Category.CAKES, 360, "🎂", "sweet", "cake", "chocolate"),
        p("Carrot Cake", "Spiced carrot cake with cream cheese frosting.", Category.CAKES, 340, "🥕", "sweet", "cake", "spiced"),
        p("Lemon Drizzle", "Zesty lemon loaf with a crunchy sugar top.", Category.CAKES, 300, "🍋", "sweet", "cake", "fruit"),
        p("Butter Croissant", "Flaky all-butter croissant, baked this morning.", Category.PASTRIES, 240, "🥐", "pastry", "breakfast"),
        p("Pain au Chocolat", "Croissant dough wrapped around dark chocolate.", Category.PASTRIES, 270, "🥐", "pastry", "sweet", "chocolate", "breakfast"),
        p("Cinnamon Swirl", "Soft pastry with cinnamon sugar.", Category.PASTRIES, 280, "🌀", "pastry", "sweet", "spiced"),
        p("Cheese Toastie", "Mature cheddar on sourdough, toasted to order.", Category.SAVOURY, 520, "🧀", "savoury", "hot", "lunch"),
        p("Tomato Soup", "Roasted tomato soup with a bread roll.", Category.SAVOURY, 480, "🍅", "savoury", "hot", "lunch"),
        p("Chicken Wrap", "Grilled chicken, salad and garlic mayo.", Category.SAVOURY, 590, "🌯", "savoury", "lunch"),
        p("Halloumi Salad", "Grilled halloumi, leaves, and pomegranate.", Category.SAVOURY, 620, "🥗", "savoury", "cold", "lunch"),
        p("Pumpkin Spice Latte", "Seasonal latte with pumpkin spice. Back soon!", Category.HOT_DRINKS, 390, "🎃", false, "hot", "coffee", "spiced", "sweet")
    )

    private fun p(
        name: String, description: String, category: Category, price: Int, emoji: String,
        vararg tags: String
    ) = Product(0, name, description, category, price, true, emoji, tags.toSet())

    private fun p(
        name: String, description: String, category: Category, price: Int, emoji: String,
        available: Boolean, vararg tags: String
    ) = Product(0, name, description, category, price, available, emoji, tags.toSet())
}
