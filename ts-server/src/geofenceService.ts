import fetch from 'node-fetch';
import { Client } from "@googlemaps/google-maps-services-js";


interface LatLng {
	latitude: number;
	longitude: number;
}

interface SimpleLatLng {
	lat: number;
	lng: number;
}

interface CompactRoute {
	summary: string;
	distance: number;
	duration: number;
	start_location: SimpleLatLng;
	end_location: SimpleLatLng;
	polyline: string;
}

const googleMapsClient = new Client({});
const GMap_API_key = "AIzaSyBoG58gmt5sB4p6dmwZBz40Doa_xn8zkks"
const EARTH_RADIUS_KM = 6371;

export async function fetchGeofences(origin: LatLng, destination: LatLng): Promise<any> {
	const { default: fetch } = await import('node-fetch');

	const url = `https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}` +
		`&destination=${destination.latitude},${destination.longitude}&alternatives=true&key=${GMap_API_key}`;

	const response = await fetch(url);
	if (!response.ok) {
		throw new Error(`Route Request Failed: ${response.statusText}`);
	}
	const jsonResponse = await response.json();
	
	const compactJson = parseRoute(jsonResponse, destination);
	return compactJson;
}


/**
 * Processes the route JSON and builds a geofence using a 3km geocircle.
 */
async function parseRoute(jsonData: any, destination: LatLng): Promise<any> {
	const routes = jsonData.routes;
	const compactRoutes: CompactRoute[] = [];
	const destinationLatLng: SimpleLatLng = { lat: destination.latitude, lng: destination.longitude };

	for (const route of routes) {
		const summary: string = route.summary;
		const polyline: string = route.overview_polyline.points;
		const leg = route.legs[0];
		const startLocation = leg.start_location;
		const endLocation = leg.end_location;

		const totalDistance: number = leg.distance.value;
		const totalDuration: number = leg.duration.value;

		const compactRoute: CompactRoute = {
			summary,
			distance: totalDistance,
			duration: totalDuration,
			start_location: startLocation,
			end_location: endLocation,
			polyline
		};

		compactRoutes.push(compactRoute);
	}

	const geofence = await generateGeofence(destinationLatLng);
	// console.log("geofence.length:" + geofence.length)
	// return { routes: compactRoutes, polygon: geofence };
	return { polygon: geofence };
}


/**
 * Computes a geofence based on a 3km radius circle.
 * Finds main street intersection points using the Google Roads API.
 */

async function generateGeofence(origin: SimpleLatLng): Promise<LatLng[]> {
    const numPoints = 36; 
    const radiusKm = 300;
    const circlePoints: LatLng[] = [];

    // Generate circle boundary points
    for (let i = 0; i < numPoints; i++) {
        const angle = (i * 10) * (Math.PI / 180); // Convert degrees to radians
        const latOffset = (radiusKm / EARTH_RADIUS_KM) * Math.sin(angle);
        const lngOffset = (radiusKm / (EARTH_RADIUS_KM * Math.cos(origin.lat * Math.PI / 180))) * Math.cos(angle);

        const newPoint: LatLng = {
            latitude: origin.lat + latOffset,
            longitude: origin.lng + lngOffset
        };

        circlePoints.push(newPoint);
    }

    const intersections = await findRoadIntersections(circlePoints);

    const polygonCoordinates = computePolygonCoordinates(intersections);

    return polygonCoordinates;
}



/**
 * Finds the intersections of the circle boundary points with roads using Google Roads API.
 */
async function findRoadIntersections(points: LatLng[]): Promise<LatLng[]> {
    const intersections: LatLng[] = [];
	const OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    for (const point of points) {
        try {
			console.log(`points: ${point.latitude} ${point.longitude}`);

			const query = `
                [out:json][timeout:10];
                (
                  node(around:1000,${point.latitude},${point.longitude});
                )->.all_nodes;

                (
				way(around:500,${point.latitude},${point.longitude}) ["highway"];
				way(around:500,${point.latitude},${point.longitude}) ["railway"];
				way(around:500,${point.latitude},${point.longitude}) ["path"];
				way(around:500,${point.latitude},${point.longitude}) ["cycleway"];
				)->.all_ways;

                node.all_nodes(if:count(ways) > 1);
                out;
            `.replace(/\s+/g, " "); 

            const url = `${OVERPASS_URL}?data=${query}`;
            // console.log("Requesting Overpass API:", url);

            const response = await fetch(url);
            const text = await response.text();
			// console.log(text)
            if (text.startsWith("<")) {
                throw new Error("Overpass API returned HTML instead of JSON (likely rate-limited).");
            }
			
			const data = JSON.parse(text);
			
			if (!data.elements) {
                console.log("⚠️ No elements found in response:", data);
                continue;
            }

			console.log(`data.elements.length: ${data.elements.length}`)

            if (data.elements.length > 0) {
                let nearestIntersection = null;
                let minDistance = Infinity;

                for (const node of data.elements) {
                    if (!node.lat || !node.lon){
						console.log('Skip invalid nodes')
						continue;
					}  // Skip invalid nodes

                    const distance = Math.hypot(
                        point.latitude - node.lat,
                        point.longitude - node.lon
                    );

                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestIntersection = { latitude: node.lat, longitude: node.lon };
                    }
                }

                if (nearestIntersection) {
					console.log("[Add intersection]" + nearestIntersection.latitude +" "+ nearestIntersection.longitude)
                    intersections.push(nearestIntersection);
                } else {
					nearestIntersection = { latitude: point.latitude, longitude: point.longitude };
					intersections.push(nearestIntersection);
                    console.log("🚨 No valid intersection found for:", point);
                }
            } else {
				var nearestIntersection = { latitude: point.latitude, longitude: point.longitude };
				intersections.push(nearestIntersection);
                console.log("❌ No nearby intersections found for:", point);
            }
        } catch (error) {
			var nearestIntersection = { latitude: point.latitude, longitude: point.longitude };
			intersections.push(nearestIntersection);
            console.log("Error fetching road intersection:", error);
        }
    }
	console.log(`fetch finishes: ${intersections.length}`)
	console.log(intersections)
    return intersections;
}



/**
 * Computes the geofence polygon by sorting points based on angle from the center.
 */
function computePolygonCoordinates(points: LatLng[]): LatLng[] {
    if (points.length < 3) {
        return [];
    }

    const center = points.reduce(
        (acc, point) => ({ lat: acc.lat + point.latitude, lng: acc.lng + point.longitude }),
        { lat: 0, lng: 0 }
    );
    center.lat /= points.length;
    center.lng /= points.length;

    const sortedPoints = points.slice().sort((a, b) => {
        const angleA = Math.atan2(a.latitude - center.lat, a.longitude - center.lng);
        const angleB = Math.atan2(b.latitude - center.lat, b.longitude - center.lng);
        return angleA - angleB;
    });
    return sortedPoints;
}
