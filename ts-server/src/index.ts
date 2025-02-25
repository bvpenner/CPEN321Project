import express, { Request, Response } from 'express';
import fetch from 'node-fetch';
import { connectDB } from '../../database/mongodb-ts/database';
import * as dbService from "../../database/mongodb-ts/userService";


const app = express();
const PORT = process.env.PORT || 3000;
const GMap_API_key = "AIzaSyBoG58gmt5sB4p6dmwZBz40Doa_xn8zkks"
const db = connectDB();


// npx nodemon --exec ts-node src/index.ts
app.use(express.json());

app.get('/', (req, res) => {
	res.send('Hello from your TypeScript server!');
});

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
	five_min_point: SimpleLatLng;
}

// Request body interface for /fetchRoute endpoint
interface RouteRequestBody {
	origin: LatLng;
	destination: LatLng;
}

interface AddTaskRequestBody {
	_id?: string;
	name: string;
	start: string;
	end: string;
	duration: number,
	location_lat: number;
	location_lng: number;
	priority: number;
	description: string;
}

/**
 * Calls the Google Directions API and processes the response.
 */
async function fetchRoute(origin: LatLng, destination: LatLng): Promise<any> {
	const { default: fetch } = await import('node-fetch');

	const url = `https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}` +
		`&destination=${destination.latitude},${destination.longitude}&alternatives=true&key=${GMap_API_key}`;

	const response = await fetch(url);
	if (!response.ok) {
		throw new Error(`Route Request Failed: ${response.statusText}`);
	}
	const jsonResponse = await response.json();
	// console.log("jsonResponse:", jsonResponse);
	const compactJson = parseRoute(jsonResponse);
	// console.log("CompactJson:", compactJson);
	return compactJson;
}

/**
 * Processes the JSON response from the Google Directions API.
 * Computes a compact route object for each route and also builds a polygon
 * (geofence) using the “5-minute away” points.
 */
function parseRoute(jsonData: any): any {
	const routes = jsonData.routes;
	const compactRoutes: CompactRoute[] = [];
	const fiveMinPoints: SimpleLatLng[] = [];
	let destinationLatLng: SimpleLatLng | null = null;

	for (const route of routes) {
		const summary: string = route.summary;
		const polyline: string = route.overview_polyline.points;
		const leg = route.legs[0];
		const startLocation = leg.start_location; // { lat, lng }
		const endLocation = leg.end_location;     // { lat, lng }
		destinationLatLng = { lat: endLocation.lat, lng: endLocation.lng };

		const totalDistance: number = leg.distance.value; // in meters
		const totalDuration: number = leg.duration.value; // in seconds

		// Compute the "5-minute away" point
		const fiveMinPointJson = find5MinPoint(leg.steps, totalDuration, totalDistance);
		const fiveMinPoint: SimpleLatLng = { lat: fiveMinPointJson.lat, lng: fiveMinPointJson.lng };

		fiveMinPoints.push(fiveMinPoint);
		console.log(`Route: 5-Minute Away Point: Lat=${fiveMinPoint.lat}, Lng=${fiveMinPoint.lng}`);

		const compactRoute: CompactRoute = {
			summary,
			distance: totalDistance,
			duration: totalDuration,
			start_location: startLocation,
			end_location: endLocation,
			polyline,
			five_min_point: fiveMinPoint
		};

		compactRoutes.push(compactRoute);
	}

	if (destinationLatLng) {
		fiveMinPoints.push(destinationLatLng);
	}


	const polygonCoordinates = computePolygonCoordinates(fiveMinPoints);

	return { routes: compactRoutes, polygon: polygonCoordinates };
}

/**
 * Determines the “5-minute away” point by iterating over the route’s steps.
 */
function find5MinPoint(steps: any[], totalDuration: number, totalDistance: number): SimpleLatLng {
	const timeThreshold = 500; // 5 minutes in seconds
	const travelDistance = (totalDistance / totalDuration) * timeThreshold;

	let accumulatedDistance = 0;

	for (let i = steps.length - 1; i >= 0; i--) {
		const step = steps[i];
		accumulatedDistance += step.distance.value;
		if (accumulatedDistance >= travelDistance) {
			return step.start_location;
		}
	}
	return steps[0].start_location;
}

/**
 * Computes the polygon (geofence) coordinates.
 * It sorts the list of points by the angle relative to a center point.
 */
function computePolygonCoordinates(points: SimpleLatLng[]): SimpleLatLng[] {
	if (points.length < 3) {
		return [];
	}

	const center = points[points.length - 1];

	const sortedPoints = points.slice().sort((a, b) => {
		const angleA = Math.atan2(a.lat - center.lat, a.lng - center.lng);
		const angleB = Math.atan2(b.lat - center.lat, b.lng - center.lng);
		return angleA - angleB;
	});
	return sortedPoints;
}

// -------------------------
// API Endpoint Definition
// -------------------------

app.post('/fetchRoute', async (req: Request<{}, any, RouteRequestBody>, res: Response): Promise<void> => {
	try {
		const { origin, destination } = req.body;
		if (!origin || !destination) {
			res.status(400).json({ error: 'Missing origin or destination coordinates.' });
			return;
		}
		console.log(`Received fetchRoute`);
		const result = await fetchRoute(origin, destination);
		res.json(result);
	} catch (error: any) {
		console.error(error);
		res.status(500).json({ error: error.message });
	}
});

app.post('/addTask', async (req: Request<{}, any, AddTaskRequestBody>, res: Response): Promise<void> => {
	try {
		const { _id, name, start, end, duration, location_lat, location_lng, priority, description } = req.body
		if(!_id){ // add new task
			var new_task_id = dbService.addTask(name, start, end, duration, location_lat, location_lng, priority, description)
			res.status(200).json({ 
				"new_task_id": new_task_id 
			});
		}
		else{ // modify existing task
			var task_id = await dbService.modifyTask(_id, name, start, end, duration, location_lat, location_lng, priority, description)
			if ( task_id == _id){
				res.status(200).json({ 
					"new_task_id": task_id 
				});
			}else{
				res.status(500).json({ 
					"error": "modifyTask failed, check server log" 
				});
			}
		}
		
	} catch (error: any) {
		console.error(error);
		res.status(500).json({ error: error.message });
	}
})

// app.post('/fetchRoute', async (req: Request<{}, any, RouteRequestBody>, res: Response): Promise<void> => {
// 
// });


app.listen(PORT, () => {
	console.log(`Server is running on port ${PORT}`);
});
