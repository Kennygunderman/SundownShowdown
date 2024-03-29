package com.kgeezy.sundownshowdown.chest

import com.kgeezy.sundownshowdown.StringRes
import com.kgeezy.sundownshowdown.util.ChestLocationFile
import com.kgeezy.sundownshowdown.util.int
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Spawning Chests with loot
 *
 */

private const val CHEST_SIZE = 26

class ChestGenerator(
    private val itemGenerator: ItemGenerator,
    fileManager: ChestLocationFile,
    var world: World? = null
) {

    private val rng = Random()
    private val yml = fileManager.getChestLocationYml()
    private val fileConfig = fileManager.configFromYml(yml)

    private val chestConfigSection: ConfigurationSection?
        get() = fileConfig
            .getConfigurationSection("${world?.name}")
            ?.getConfigurationSection("chests")

    /**
     * creates a chest above the block that the Player is looking at, as long as the
     * block isn't Air.
     *
     */
    fun createChestAboveBlock(player: Player, block: Block): Boolean {
        if (block.type == Material.AIR) {
            player.sendMessage(StringRes.CANT_PLACE_CHEST_IN_AIR)
            return false
        }

        world?.let { w ->
            val chestBlock = w.getBlockAt(block.location.apply { y++ })
            createChestAtBlock(chestBlock)
            saveChestLocation(chestBlock.location)
        }

        return true
    }

    private fun createChestAtBlock(block: Block) {
        block.type = Material.CHEST

        val chest = block.state as Chest
        generateChestContents { inventoryIndex, item ->
            chest.inventory.setItem(inventoryIndex, item)
        }
    }

    private fun generateChestContents(callback: (inventoryIndex: Int, item: ItemStack) -> Unit) {
        val rolls = rng.int(2, 5)

        for (i in 0 until rolls) {
            val index = rng.nextInt(CHEST_SIZE)
            callback(index, itemGenerator.generateRandomItem())
        }
    }


    private fun saveChestLocation(location: Location) {
        val uid = UUID.randomUUID().toString()
        world?.name.let { worldName ->
            fileConfig.set("$worldName.chests.$uid.x", location.x)
            fileConfig.set("$worldName.chests.$uid.y", location.y)
            fileConfig.set("$worldName.chests.$uid.z", location.z)
            fileConfig.save(yml)
        }
    }

    fun getChestLocations(): List<Location> = mutableListOf<Location>().apply {
        chestConfigSection?.getKeys(false)?.forEach { chestId ->
            getXyzForChestId(chestId) { x, y, z ->
                if (x != null && y != null && z != null) {
                    val location = Location(world, x, y, z)
                    add(location)
                }
            }
        }
    }

    private fun getXyzForChestId(id: String, callback: (x: Double?, y: Double?, z: Double?) -> Unit) {
        val x = chestConfigSection?.getConfigurationSection(id)?.get("x") as? Double
        val y = chestConfigSection?.getConfigurationSection(id)?.get("y") as? Double
        val z = chestConfigSection?.getConfigurationSection(id)?.get("z") as? Double
        callback.invoke(x, y, z)
    }

    fun restockChests() {
        getChestLocations().forEach { chestLocation ->
            world?.getBlockAt(chestLocation)?.let { block ->
                (block.state as? Chest)?.inventory?.clear()
                createChestAtBlock(block)
            }
        }
    }

    /**
     * Removes a chest
     */
    fun removeChest(location: Location): Boolean {
        val block = world?.getBlockAt(location)
        var blockFound = false
        if (block?.state is Chest) {
            chestConfigSection?.getKeys(false)?.forEach { chestId ->
                getXyzForChestId(chestId) { x, y, z ->
                    if (x == location.x && y == location.y && z == location.z) {
                        blockFound = true
                        block.setType(Material.AIR, false)
                        chestConfigSection?.set(chestId, null)
                        fileConfig.save(yml)
                    }
                }
            }
        }
        return blockFound
    }

    fun removeAll() {
        getChestLocations().forEach { loc ->
            world?.getBlockAt(loc)?.type = Material.AIR
        }

        fileConfig.getConfigurationSection("${world?.name}")?.set("chests", null)
        fileConfig.save(yml)
    }
}
