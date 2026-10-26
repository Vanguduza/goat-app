package com.farmos.core.database

import androidx.room.*

/** Real rabbitry housing/service facts, never inferred from programme capacity or a planned wave. */
@Entity(
    tableName = "rabbit_housing",
    primaryKeys = ["farmId", "animalId"],
    foreignKeys = [
        ForeignKey(entity=AnimalEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "animalId"],onDelete=ForeignKey.NO_ACTION),
        ForeignKey(entity=RabbitCageEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "cageId"],onDelete=ForeignKey.NO_ACTION),
    ],
    indices = [Index(value=["farmId", "cageId"])],
)
data class RabbitHousingEntity(
    val farmId: String,
    val animalId: String,
    val cageId: String?,
    val role: String?,
    val changedAtEpochMillis: Long,
    val version: Long,
)

@Entity(
    tableName = "rabbit_buck_services",
    primaryKeys = ["farmId", "id"],
    foreignKeys = [
        ForeignKey(entity=RabbitCageEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "cageId"],onDelete=ForeignKey.NO_ACTION),
        ForeignKey(entity=AnimalEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "buckId"],onDelete=ForeignKey.NO_ACTION),
        ForeignKey(entity=RabbitWaveEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "waveId"],onDelete=ForeignKey.NO_ACTION),
    ],
    indices = [Index(value=["farmId", "cageId"]), Index(value=["farmId", "buckId"]), Index(value=["farmId", "waveId"])],
)
data class RabbitBuckServiceEntity(
    val farmId: String,
    val id: String,
    val cageId: String,
    val buckId: String,
    val waveId: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val notes: String,
    val version: Long,
)

@Entity(
    tableName = "rabbit_pair_matings",
    primaryKeys = ["farmId", "id"],
    foreignKeys = [
        ForeignKey(entity=RabbitCageEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "cageId"],onDelete=ForeignKey.NO_ACTION),
        ForeignKey(entity=AnimalEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "doeId"],onDelete=ForeignKey.NO_ACTION),
        ForeignKey(entity=AnimalEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "buckId"],onDelete=ForeignKey.NO_ACTION),
        ForeignKey(entity=RabbitWaveEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "waveId"],onDelete=ForeignKey.NO_ACTION),
    ],
    indices = [Index(value=["farmId", "cageId"]), Index(value=["farmId", "doeId"]), Index(value=["farmId", "buckId"]), Index(value=["farmId", "waveId"])],
)
data class RabbitPairMatingEntity(
    val farmId: String,
    val id: String,
    val cageId: String,
    val doeId: String,
    val buckId: String,
    val waveId: String?,
    val matedAtEpochMillis: Long,
    val notes: String,
)

@Entity(
    tableName = "rabbit_nest_assignments",
    primaryKeys = ["farmId", "boxId"],
    foreignKeys = [
        ForeignKey(entity=RabbitNestBoxEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "boxId"],onDelete=ForeignKey.NO_ACTION),
        ForeignKey(entity=AnimalEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "doeId"],onDelete=ForeignKey.NO_ACTION),
        ForeignKey(entity=RabbitWaveEntity::class,parentColumns=["farmId", "id"],childColumns=["farmId", "waveId"],onDelete=ForeignKey.NO_ACTION),
    ],
    indices = [Index(value=["farmId", "doeId"]), Index(value=["farmId", "waveId"])],
)
data class RabbitNestAssignmentEntity(
    val farmId: String,
    val boxId: String,
    val doeId: String?,
    val waveId: String?,
    val changedAtEpochMillis: Long,
    val version: Long,
)

@Dao
interface RabbitryDao {
    @Query("SELECT * FROM rabbit_cages WHERE farmId=:farmId AND id=:id LIMIT 1")
    suspend fun cage(farmId:String,id:String):RabbitCageEntity?
    @Query("SELECT * FROM rabbit_nest_boxes WHERE farmId=:farmId AND id=:id LIMIT 1")
    suspend fun box(farmId:String,id:String):RabbitNestBoxEntity?
    @Query("SELECT * FROM rabbit_breeding_waves WHERE farmId=:farmId AND id=:id LIMIT 1")
    suspend fun wave(farmId:String,id:String):RabbitWaveEntity?
    @Query("SELECT * FROM rabbit_housing WHERE farmId=:farmId ORDER BY cageId,animalId")
    suspend fun housing(farmId:String):List<RabbitHousingEntity>
    @Query("SELECT * FROM rabbit_housing WHERE farmId=:farmId AND animalId=:animalId LIMIT 1")
    suspend fun housingFor(farmId:String,animalId:String):RabbitHousingEntity?
    @Query("SELECT * FROM rabbit_housing WHERE farmId=:farmId AND cageId=:cageId ORDER BY role,animalId")
    suspend fun occupants(farmId:String,cageId:String):List<RabbitHousingEntity>
    @Upsert suspend fun setHousing(row:RabbitHousingEntity)
    @Query("SELECT * FROM rabbit_buck_services WHERE farmId=:farmId ORDER BY startedAtEpochMillis DESC,id")
    suspend fun services(farmId:String):List<RabbitBuckServiceEntity>
    @Query("SELECT * FROM rabbit_buck_services WHERE farmId=:farmId AND id=:id LIMIT 1")
    suspend fun service(farmId:String,id:String):RabbitBuckServiceEntity?
    @Query("SELECT * FROM rabbit_buck_services WHERE farmId=:farmId AND buckId=:buckId AND endedAtEpochMillis IS NULL ORDER BY startedAtEpochMillis,id")
    suspend fun activeServices(farmId:String,buckId:String):List<RabbitBuckServiceEntity>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertService(row:RabbitBuckServiceEntity)
    @Query("UPDATE rabbit_buck_services SET endedAtEpochMillis=:ended,version=version+1 WHERE farmId=:farmId AND id=:id AND version=:expected AND endedAtEpochMillis IS NULL")
    suspend fun endService(farmId:String,id:String,ended:Long,expected:Long):Int
    @Query("SELECT * FROM rabbit_pair_matings WHERE farmId=:farmId ORDER BY matedAtEpochMillis DESC,id")
    suspend fun matings(farmId:String):List<RabbitPairMatingEntity>
    @Query("SELECT * FROM rabbit_pair_matings WHERE farmId=:farmId AND (doeId=:animalId OR buckId=:animalId) ORDER BY matedAtEpochMillis DESC,id")
    suspend fun matingsFor(farmId:String,animalId:String):List<RabbitPairMatingEntity>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertMating(row:RabbitPairMatingEntity)
    @Query("SELECT * FROM rabbit_nest_assignments WHERE farmId=:farmId ORDER BY boxId")
    suspend fun assignments(farmId:String):List<RabbitNestAssignmentEntity>
    @Query("SELECT * FROM rabbit_nest_assignments WHERE farmId=:farmId AND boxId=:boxId LIMIT 1")
    suspend fun assignment(farmId:String,boxId:String):RabbitNestAssignmentEntity?
    @Query("SELECT * FROM rabbit_nest_assignments WHERE farmId=:farmId AND doeId=:doeId ORDER BY boxId")
    suspend fun nestsForDoe(farmId:String,doeId:String):List<RabbitNestAssignmentEntity>
    @Upsert suspend fun setAssignment(row:RabbitNestAssignmentEntity)
}
