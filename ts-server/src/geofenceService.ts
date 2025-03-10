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

    for (const point of points) {
        try {
            const response = await googleMapsClient.snapToRoads({
                params: {
                    path: [{ lat: point.latitude, lng: point.longitude }], // ✅ Fix: Pass an array instead of a string
                    interpolate: false,
                    key: GMap_API_key
                }
            });

            if (response.data.snappedPoints && response.data.snappedPoints.length > 0) {
                const snappedPoint = response.data.snappedPoints[0].location;
                intersections.push({ latitude: snappedPoint.latitude, longitude: snappedPoint.longitude });
            }
        } catch (error) {
            console.error("Error fetching road intersection:", error);
        }
    }
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
