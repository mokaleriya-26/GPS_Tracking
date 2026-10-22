package com.gps.tracking.service;

import com.gps.tracking.dto.SimulationRouteDTO;
import com.gps.tracking.dto.SimulationWaypointDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulationRouteProvider {

    private final Map<String, SimulationRouteDTO> routes = new ConcurrentHashMap<>();

    public SimulationRouteProvider() {
        initRoutes();
    }

    private void initRoutes() {
        // Route 1: Mumbai - Pune Highway (Expressway Freight Route)
        List<SimulationWaypointDTO> r1Waypoints = new ArrayList<>();
        r1Waypoints.add(wp(0, "19.0069", "73.1018", "JNPT-Kalamboli Access Road", "60", "45", "Kalamboli Freight Hub"));
        r1Waypoints.add(wp(1, "18.9902", "73.1205", "Sion-Panvel Expressway", "80", "65", "Panvel Flyover"));
        r1Waypoints.add(wp(2, "18.9715", "73.1492", "Mumbai-Pune Expressway (NH-48)", "80", "70", "Kon Expressway Entry"));
        r1Waypoints.add(wp(3, "18.9198", "73.2356", "Mumbai-Pune Expressway", "80", "74", "Chowk Interchange"));
        r1Waypoints.add(wp(4, "18.8352", "73.2847", "Mumbai-Pune Expressway", "80", "50", "Khalapur Food Mall & Toll"));
        r1Waypoints.add(wp(5, "18.8055", "73.3102", "Mumbai-Pune Expressway", "60", "55", "Bhatan Tunnel"));
        r1Waypoints.add(wp(6, "18.7758", "73.3421", "Mumbai-Pune Expressway", "60", "52", "Madap Tunnel"));
        r1Waypoints.add(wp(7, "18.7510", "73.3650", "Mumbai-Pune Expressway", "80", "65", "Khopoli Viaduct"));
        r1Waypoints.add(wp(8, "18.7290", "73.3880", "Mumbai-Pune Expressway", "60", "48", "Adoshi Tunnel Curve"));
        r1Waypoints.add(wp(9, "18.7450", "73.3980", "Mumbai-Pune Expressway - Ghat", "50", "42", "Khandala Ghat Section"));
        r1Waypoints.add(wp(10, "18.7550", "73.4150", "Mumbai-Pune Expressway", "80", "68", "Lonavala Bypass"));
        r1Waypoints.add(wp(11, "18.7580", "73.4320", "Mumbai-Pune Expressway", "80", "72", "Valvan Interchange"));
        r1Waypoints.add(wp(12, "18.7410", "73.4680", "Mumbai-Pune Expressway", "80", "70", "Kusgaon Toll Section"));
        r1Waypoints.add(wp(13, "18.7300", "73.5350", "Mumbai-Pune Expressway", "60", "58", "Kamshet Tunnel Road"));
        r1Waypoints.add(wp(14, "18.7210", "73.5780", "Mumbai-Pune Expressway", "80", "75", "Pawna River Bridge"));
        r1Waypoints.add(wp(15, "18.7180", "73.6300", "Mumbai-Pune Expressway", "80", "72", "Vadgaon Phata"));
        r1Waypoints.add(wp(16, "18.7150", "73.6700", "Mumbai-Pune Expressway", "80", "60", "Talegaon Dabhade Toll"));
        r1Waypoints.add(wp(17, "18.7050", "73.6950", "Mumbai-Pune Expressway", "80", "68", "Urse Toll Plaza"));
        r1Waypoints.add(wp(18, "18.6850", "73.7150", "Old Mumbai-Pune Highway Link", "80", "65", "Somatane Phata"));
        r1Waypoints.add(wp(19, "18.6650", "73.7350", "Dehu Road Cantt Highway", "80", "62", "Dehu Road Cantt Overpass"));
        r1Waypoints.add(wp(20, "18.6480", "73.7420", "Mumbai-Pune Expressway Terminus", "60", "52", "Kiwale Interchange"));
        r1Waypoints.add(wp(21, "18.6320", "73.7480", "Aundh-Ravet BRTS Road", "60", "48", "Ravet Flyover"));
        r1Waypoints.add(wp(22, "18.6180", "73.7510", "Katraj-Dehu Road Bypass", "60", "45", "Punawale Junction"));
        r1Waypoints.add(wp(23, "18.5980", "73.7580", "Hinjawadi Link Road", "50", "38", "Wakad Highway Crossing"));
        r1Waypoints.add(wp(24, "18.5912", "73.7389", "Phase 1 Logistics Hub Road", "40", "28", "Hinjawadi Logistics Park"));

        SimulationRouteDTO route1 = SimulationRouteDTO.builder()
                .id("mumbai-pune-expressway")
                .name("Mumbai - Pune Highway (Expressway Freight Route)")
                .description("High-speed intercity freight route connecting Navi Mumbai to Pune Hinjawadi Logistics Corridor.")
                .origin("Kalamboli Freight Terminal, Navi Mumbai")
                .destination("Hinjawadi Logistics Park, Pune")
                .estimatedDistanceKm(new BigDecimal("94.5"))
                .estimatedMinutes(95)
                .waypoints(r1Waypoints)
                .build();
        routes.put(route1.getId(), route1);

        // Route 2: Thane - JNPT Port Logistics Corridor
        List<SimulationWaypointDTO> r2Waypoints = new ArrayList<>();
        r2Waypoints.add(wp(0, "19.2183", "72.9781", "Eastern Express Highway", "60", "42", "Thane Majiwada Hub"));
        r2Waypoints.add(wp(1, "19.1990", "72.9850", "Thane-Belapur Road", "60", "50", "Kalwa Bridge"));
        r2Waypoints.add(wp(2, "19.1750", "72.9980", "Thane-Belapur Industrial Road", "60", "55", "Digha MIDC"));
        r2Waypoints.add(wp(3, "19.1550", "73.0030", "Airoli Knowledge Park Road", "60", "52", "Airoli Station"));
        r2Waypoints.add(wp(4, "19.1300", "73.0100", "Thane-Belapur Road", "60", "56", "Rabale Industrial Area"));
        r2Waypoints.add(wp(5, "19.1100", "73.0150", "Thane-Belapur Road", "60", "58", "Ghansoli Flyover"));
        r2Waypoints.add(wp(6, "19.0850", "73.0180", "Thane-Belapur Road", "60", "55", "Kopar Khairane"));
        r2Waypoints.add(wp(7, "19.0600", "73.0210", "Sion-Panvel Highway Link", "70", "62", "Turbhe Naka Flyover"));
        r2Waypoints.add(wp(8, "19.0400", "73.0250", "Sion-Panvel Highway", "80", "68", "Sanpada Flyover"));
        r2Waypoints.add(wp(9, "19.0250", "73.0300", "Sion-Panvel Highway", "80", "70", "Nerul LP Flyover"));
        r2Waypoints.add(wp(10, "19.0050", "73.0380", "Sion-Panvel Highway", "80", "72", "CBD Belapur Flyover"));
        r2Waypoints.add(wp(11, "18.9850", "73.0450", "JNPT Road (NH-348A)", "70", "60", "Uran Phata Junction"));
        r2Waypoints.add(wp(12, "18.9680", "73.0350", "Port Connectivity Expressway", "70", "64", "Kille Gaon Overpass"));
        r2Waypoints.add(wp(13, "18.9550", "73.0150", "Port Access Highway", "70", "65", "Ulwe Node Sector"));
        r2Waypoints.add(wp(14, "18.9480", "72.9900", "JNPT Port Road", "60", "55", "Dronagiri Logistics Node"));
        r2Waypoints.add(wp(15, "18.9499", "72.9510", "Container Terminal Access Road", "40", "30", "JNPT Port Terminal 4"));

        SimulationRouteDTO route2 = SimulationRouteDTO.builder()
                .id("thane-jnpt-corridor")
                .name("Thane - JNPT Port Logistics Corridor")
                .description("Major heavy transport corridor from Thane industrial belt directly to JNPT container terminals.")
                .origin("Thane Majiwada Freight Hub")
                .destination("JNPT Port Terminal 4, Navi Mumbai")
                .estimatedDistanceKm(new BigDecimal("42.8"))
                .estimatedMinutes(55)
                .waypoints(r2Waypoints)
                .build();
        routes.put(route2.getId(), route2);
    }

    private SimulationWaypointDTO wp(int seq, String lat, String lng, String road, String limit, String defSpeed, String landmark) {
        return SimulationWaypointDTO.builder()
                .sequence(seq)
                .latitude(new BigDecimal(lat))
                .longitude(new BigDecimal(lng))
                .roadName(road)
                .speedLimitKmph(new BigDecimal(limit))
                .defaultSpeedKmph(new BigDecimal(defSpeed))
                .landmark(landmark)
                .build();
    }

    public List<SimulationRouteDTO> getAllRoutes() {
        return new ArrayList<>(routes.values());
    }

    public SimulationRouteDTO getRoute(String id) {
        if (id == null || !routes.containsKey(id)) {
            return routes.get("mumbai-pune-expressway");
        }
        return routes.get(id);
    }
}
