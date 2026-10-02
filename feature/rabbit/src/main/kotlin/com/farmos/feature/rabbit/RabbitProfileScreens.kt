package com.farmos.feature.rabbit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass

/** A registered breeding rabbit: a doe or a buck. */
data class RabbitAnimalView(val animalId: String, val tag: String, val name: String?, val isDoe: Boolean, val status: String) {
    val label: String get() = listOfNotNull(tag, name?.takeIf { it.isNotBlank() }).joinToString(" · ")
    val active: Boolean get() = status == "active"
}

/** FOS-RABBIT-002 — breeding animals; each opens its doe or buck profile. */
@Composable
internal fun RabbitAnimalsScreen(rabbits: List<RabbitAnimalView>, rabbitCount: Int?, error: String?, onOpen: (RabbitAnimalView) -> Unit, onBack: () -> Unit) {
    FarmOperationalPage("FOS-RABBIT-002", "Breeding animals", "Registered does and bucks on this device, by tag.", onBack = onBack) {
        rabbitCount?.takeIf { it > rabbits.size }?.let { Text("Showing the first ${rabbits.size} of $it rabbits by tag.", modifier = Modifier.testTag("rabbit-list-bounded")) }
        if (rabbits.isEmpty()) {
            AnimalFarmEmptyState("No rabbits registered")
        } else {
            FarmOperationalSection("Does and bucks") {
                rabbits.forEachIndexed { index, rabbit ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                            .clickable(role = Role.Button) { onOpen(rabbit) }
                            .padding(vertical = 6.dp).testTag("rabbit:${rabbit.animalId}"),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(rabbit.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${if (rabbit.isDoe) "Doe" else "Buck"} · ${rabbit.status}", color = AnimalFarmTheme.colors.mutedInk)
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * FOS-RABBIT-003 Doe profile and FOS-RABBIT-004 Buck profile: identity and status, with the rabbit's exit
 * from the rabbitry (death, cull or sale, and its reversal) recorded in [exit] (owner decision D-022).
 */
@Composable
internal fun RabbitProfileScreen(
    rabbit: RabbitAnimalView,
    exit: @Composable (RabbitAnimalView) -> Unit,
    onBack: () -> Unit,
    /** Photos and documents of this rabbit (D-015), when the host provides them. */
    attachments: @Composable (RabbitAnimalView) -> Unit = {},
) {
    val screenId = if (rabbit.isDoe) "FOS-RABBIT-003" else "FOS-RABBIT-004"
    val kind = if (rabbit.isDoe) "Doe" else "Buck"
    FarmOperationalPage(screenId, rabbit.label, "$kind · ${rabbit.status}", FarmVisualClass.I3, onBack, backLabel = "Breeding animals") {
        FarmOperationalSection("Identity") {
            Text("Tag ${rabbit.tag}")
            Text(rabbit.name?.takeIf { it.isNotBlank() }?.let { "Name $it" } ?: "No name recorded.")
            Text(if (rabbit.active) "In the rabbitry" else "Left the rabbitry: ${rabbit.status}")
        }
        FarmOperationalSection(if (rabbit.active) "Leaving the rabbitry" else "Exit record") {
            exit(rabbit)
        }
        attachments(rabbit)
    }
}
