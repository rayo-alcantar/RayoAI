package com.rayoai.domain.model.proactive

import androidx.annotation.StringRes
import com.rayoai.R

enum class StandardProactiveTarget(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val promptEs: String,
    val promptEn: String
) {
    PEOPLE(
        key = "people",
        titleRes = R.string.proactive_target_people,
        descriptionRes = R.string.proactive_target_people_desc,
        promptEs = "Personas en el campo de visión o acercándose",
        promptEn = "People in the field of view or approaching"
    ),
    VEHICLES(
        key = "vehicles",
        titleRes = R.string.proactive_target_vehicles,
        descriptionRes = R.string.proactive_target_vehicles_desc,
        promptEs = "Autos, colectivos, motocicletas, bicicletas o vehículos en movimiento o detenidos",
        promptEn = "Cars, buses, motorcycles, bicycles or vehicles in motion or stopped"
    ),
    TRAFFIC_LIGHTS(
        key = "traffic_lights",
        titleRes = R.string.proactive_target_traffic_lights,
        descriptionRes = R.string.proactive_target_traffic_lights_desc,
        promptEs = "Semáforos peatonales o vehiculares con indicación de su estado o color si es visible",
        promptEn = "Pedestrian or traffic signals indicating status or light color if visible"
    ),
    CROSSINGS(
        key = "crossings",
        titleRes = R.string.proactive_target_crossings,
        descriptionRes = R.string.proactive_target_crossings_desc,
        promptEs = "Cruces de calle, esquinas, bajadas de cordón, bordes de acera o sendas peatonales",
        promptEn = "Street crossings, corners, curb ramps, sidewalk edges or pedestrian crosswalks"
    ),
    OBSTACLES(
        key = "obstacles",
        titleRes = R.string.proactive_target_obstacles,
        descriptionRes = R.string.proactive_target_obstacles_desc,
        promptEs = "Obstáculos en el paso, baches, pozos, desniveles del suelo, escaleras, postes o ramas bajas",
        promptEn = "Obstacles in the path, potholes, holes, uneven ground, stairs, poles or low branches"
    ),
    DOORS(
        key = "doors",
        titleRes = R.string.proactive_target_doors,
        descriptionRes = R.string.proactive_target_doors_desc,
        promptEs = "Puertas de acceso, entradas o salidas, y si están abiertas o cerradas",
        promptEn = "Access doors, entrances or exits, and whether they are open or closed"
    ),
    SEATS(
        key = "seats",
        titleRes = R.string.proactive_target_seats,
        descriptionRes = R.string.proactive_target_seats_desc,
        promptEs = "Asientos, bancos o sillas libres disponibles para sentarse",
        promptEn = "Available seats, benches or chairs to sit down"
    ),
    OBJECTS(
        key = "objects",
        titleRes = R.string.proactive_target_objects,
        descriptionRes = R.string.proactive_target_objects_desc,
        promptEs = "Objetos personales o cotidianos (llaves, celular, billetera, bastón, gafas, tazas)",
        promptEn = "Personal or everyday objects (keys, phone, wallet, cane, glasses, cups)"
    );

    companion object {
        fun fromKey(key: String): StandardProactiveTarget? {
            return entries.firstOrNull { it.key == key }
        }
    }
}
