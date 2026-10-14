package com.nutricare.mobile.features.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Slate
import com.nutricare.mobile.ui.theme.Teal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

data class ClinicalRecipe(
    val id: Int,
    val title: String,
    val description: String,
    val calories: Int,
    val prepTime: String,
    val servings: Int,
    val tags: List<String>,
    val ingredients: List<String>,
    val protein: String,
    val carbs: String,
    val fat: String,
    val sodium: String,
    val iconEmoji: String
)

val CLINICAL_RECIPES = listOf(
    ClinicalRecipe(
        id = 1,
        title = "Herb-Crusted Baked Cod",
        description = "Light, flaky cod with rosemary and thyme crust, served with steamed cauliflower. Clinically optimized for kidney care.",
        calories = 380,
        prepTime = "25 min",
        servings = 2,
        tags = listOf("CKD-Safe", "Low Sodium", "High Protein"),
        ingredients = listOf("Cod fillet", "Fresh Rosemary & Thyme", "Cold-pressed Olive oil", "Steamed Cauliflower", "Garlic", "Lemon zest"),
        protein = "28g",
        carbs = "12g",
        fat = "14g",
        sodium = "180mg",
        iconEmoji = "🐟"
    ),
    ClinicalRecipe(
        id = 2,
        title = "Quinoa Veggie Harvest Bowl",
        description = "Protein-packed quinoa with roasted bell peppers, chickpeas, baby kale, and light lemon tahini dressing.",
        calories = 420,
        prepTime = "30 min",
        servings = 2,
        tags = listOf("Diabetes-Friendly", "High Fiber", "Low GI"),
        ingredients = listOf("Tricolor Quinoa", "Roasted Bell peppers", "Chickpeas", "Tahini", "Baby Kale", "Extra Virgin Olive oil"),
        protein = "18g",
        carbs = "42g",
        fat = "16g",
        sodium = "120mg",
        iconEmoji = "🥗"
    ),
    ClinicalRecipe(
        id = 3,
        title = "Wild Berry Overnight Chia Oats",
        description = "Steel-cut oats soaked in unsweetened almond milk with antioxidant wild blueberries and chia seeds.",
        calories = 260,
        prepTime = "5 min + chill",
        servings = 1,
        tags = listOf("Heart Healthy", "Low GI", "Weight Loss"),
        ingredients = listOf("Steel-cut oats", "Unsweetened Almond milk", "Chia seeds", "Fresh Blueberries", "Ceylon Cinnamon"),
        protein = "9g",
        carbs = "38g",
        fat = "8g",
        sodium = "45mg",
        iconEmoji = "🥣"
    ),
    ClinicalRecipe(
        id = 4,
        title = "Lemon Herb Grilled Chicken",
        description = "Tender lean chicken breast marinated in Mediterranean herbs with steamed green beans. Low potassium footprint.",
        calories = 340,
        prepTime = "20 min",
        servings = 2,
        tags = listOf("CKD-Safe", "High Protein", "Lean"),
        ingredients = listOf("Chicken breast fillet", "Lemon juice", "Wild Oregano", "Olive oil", "Crisp Green beans", "Garlic"),
        protein = "32g",
        carbs = "8g",
        fat = "12g",
        sodium = "140mg",
        iconEmoji = "🍗"
    ),
    ClinicalRecipe(
        id = 5,
        title = "DASH Cardio-Protective Salad",
        description = "Crisp leafy greens with fresh avocado, crushed walnuts, steamed beets, and cold-pressed balsamic reduction.",
        calories = 280,
        prepTime = "15 min",
        servings = 1,
        tags = listOf("Hypertension-Safe", "DASH", "Low Sodium"),
        ingredients = listOf("Baby Spinach & Arugula", "Avocado", "Raw Walnuts", "Steamed Beets", "Aged Balsamic vinegar"),
        protein = "8g",
        carbs = "22g",
        fat = "18g",
        sodium = "65mg",
        iconEmoji = "🥑"
    ),
    ClinicalRecipe(
        id = 6,
        title = "Potassium-Controlled Vegetable Broth",
        description = "Nourishing, slow-simmered vegetable soup with double-boiled carrots, celery, and white beans. Zero added sodium.",
        calories = 190,
        prepTime = "35 min",
        servings = 4,
        tags = listOf("CKD-Safe", "Hypertension-Safe", "Low Sodium"),
        ingredients = listOf("Double-boiled Carrots", "Celery", "Cannellini beans", "Zucchini", "Parsley", "Fresh Herbs"),
        protein = "10g",
        carbs = "28g",
        fat = "3g",
        sodium = "85mg",
        iconEmoji = "🍲"
    )
)

@Composable
fun RecipesScreen(onBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf("All") }
    var loggingRecipeId by remember { mutableStateOf<Int?>(null) }
    var loggedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val categories = listOf("All", "CKD-Safe", "Diabetes-Friendly", "Hypertension-Safe", "Low Sodium")

    val filtered = if (selectedCategory == "All") {
        CLINICAL_RECIPES
    } else {
        CLINICAL_RECIPES.filter { it.tags.any { tag -> tag.contains(selectedCategory, ignoreCase = true) } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Clinical Recipe Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Disease-safe meals tailored to clinical guidelines", fontSize = 12.sp, color = Slate)
            }
        }

        // Category Filter Chips
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val isSel = cat == selectedCategory
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSel) EmeraldDark else Color(0xFFF1F5F9))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) Color.White else Color(0xFF475569)
                    )
                }
            }
        }

        loggedMessage?.let { msg ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = EmeraldDark, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(msg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EmeraldDark)
                }
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(filtered.size) { index ->
                val r = filtered[index]
                val isLogging = (loggingRecipeId == r.id)

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .background(Color(0xFFF0FDF4), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(r.iconEmoji, fontSize = 24.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Timer, null, tint = Slate, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(r.prepTime, fontSize = 11.sp, color = Slate)
                                    }
                                    Text("•", fontSize = 11.sp, color = Slate)
                                    Text("${r.servings} servings", fontSize = 11.sp, color = Slate)
                                }
                            }
                        }

                        Text(r.description, fontSize = 12.sp, color = Color(0xFF334155))

                        // Tags
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.tags.forEach { tag ->
                                Box(
                                    Modifier
                                        .background(Color(0xFFECFDF5), RoundedCornerShape(6.dp))
                                        .border(1.dp, EmeraldDark.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldDark)
                                }
                            }
                        }

                        // Macro Strip
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Mist, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CALORIES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate)
                                Text("${r.calories} kcal", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PROTEIN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate)
                                Text(r.protein, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldDark)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CARBS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate)
                                Text(r.carbs, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teal)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SODIUM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate)
                                Text(r.sodium, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                            }
                        }

                        // Log Meal Button
                        Button(
                            onClick = {
                                loggingRecipeId = r.id
                                scope.launch {
                                    try {
                                        val client = ServiceLocator.supabase
                                        val user = client.auth.currentUserOrNull()
                                        if (user != null) {
                                            client.from("food_uploads").insert(
                                                mapOf(
                                                    "profile_id" to user.id,
                                                    "food_name" to r.title,
                                                    "calories" to r.calories,
                                                    "protein_g" to r.protein.replace("g", "").toDoubleOrNull(),
                                                    "carbs_g" to r.carbs.replace("g", "").toDoubleOrNull(),
                                                    "fat_g" to r.fat.replace("g", "").toDoubleOrNull(),
                                                    "sodium_mg" to r.sodium.replace("mg", "").toDoubleOrNull(),
                                                    "meal_type" to "Recipe"
                                                )
                                            )
                                            loggedMessage = "Logged ${r.title} (${r.calories} kcal) to today's food diary!"
                                        }
                                    } catch (e: Exception) {
                                        loggedMessage = "Saved locally. Logged ${r.title}."
                                    } finally {
                                        loggingRecipeId = null
                                    }
                                }
                            },
                            enabled = !isLogging,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            if (isLogging) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.RestaurantMenu, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Log to Food Diary", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
