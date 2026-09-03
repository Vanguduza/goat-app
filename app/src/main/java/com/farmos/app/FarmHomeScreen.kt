package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.core.design.FosDimens

enum class FarmModule {
    HOME, GOAT, RABBIT, SHEEP, CATTLE, POULTRY, TASKS, HEALTH, MONEY, INVENTORY,
    GROUPS, PASTURE, LABOUR, ASSETS, FEED, WATER, SALES, PROCUREMENT, WAITLIST
}

@Composable
fun FarmHomeScreen(
    farmName: String?,
    onOpen: (FarmModule) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text(farmName ?: "Farm home", style = MaterialTheme.typography.titleLarge)
        Text("Species tiles open native records. Shared tools stay farm-wide.")
        Button(onClick = { onOpen(FarmModule.GOAT) }, modifier = Modifier.fillMaxWidth()) { Text("Goats") }
        Button(onClick = { onOpen(FarmModule.RABBIT) }, modifier = Modifier.fillMaxWidth()) { Text("Rabbits") }
        Button(onClick = { onOpen(FarmModule.SHEEP) }, modifier = Modifier.fillMaxWidth()) { Text("Sheep") }
        Button(onClick = { onOpen(FarmModule.CATTLE) }, modifier = Modifier.fillMaxWidth()) { Text("Cattle") }
        Button(onClick = { onOpen(FarmModule.POULTRY) }, modifier = Modifier.fillMaxWidth()) { Text("Poultry") }
        Button(onClick = { onOpen(FarmModule.TASKS) }, modifier = Modifier.fillMaxWidth()) { Text("Tasks") }
        Button(onClick = { onOpen(FarmModule.HEALTH) }, modifier = Modifier.fillMaxWidth()) { Text("Health") }
        Button(onClick = { onOpen(FarmModule.MONEY) }, modifier = Modifier.fillMaxWidth()) { Text("Money") }
        Button(onClick = { onOpen(FarmModule.INVENTORY) }, modifier = Modifier.fillMaxWidth()) { Text("Inventory") }
        Button(onClick = { onOpen(FarmModule.GROUPS) }, modifier = Modifier.fillMaxWidth()) { Text("Groups") }
        Button(onClick = { onOpen(FarmModule.PASTURE) }, modifier = Modifier.fillMaxWidth()) { Text("Pasture") }
        Button(onClick = { onOpen(FarmModule.LABOUR) }, modifier = Modifier.fillMaxWidth()) { Text("Labour") }
        Button(onClick = { onOpen(FarmModule.ASSETS) }, modifier = Modifier.fillMaxWidth()) { Text("Assets") }
        Button(onClick = { onOpen(FarmModule.FEED) }, modifier = Modifier.fillMaxWidth()) { Text("Feed") }
        Button(onClick = { onOpen(FarmModule.WATER) }, modifier = Modifier.fillMaxWidth()) { Text("Water") }
        Button(onClick = { onOpen(FarmModule.SALES) }, modifier = Modifier.fillMaxWidth()) { Text("Sales") }
        Button(onClick = { onOpen(FarmModule.PROCUREMENT) }, modifier = Modifier.fillMaxWidth()) { Text("Procurement") }
        Button(onClick = { onOpen(FarmModule.WAITLIST) }, modifier = Modifier.fillMaxWidth()) { Text("Rabbit waitlist") }
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }
}
