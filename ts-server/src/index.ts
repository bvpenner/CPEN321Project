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
	start_time: string;
	end_time: string;
	duration: number,
	location_lat: number;
	location_lng: number;
	priority: number;
	description: string;
}

class Task {
	public start_time: number;
	public end_time: number;    // latest time to reach a task
	public duration: number;    // in minutes
	public location_lat: number;
	public location_lng: number;
	public priority: number;
	public description: string;

	constructor(start_time: number, end_time: number, duration: number, location_lat: number, location_lng: number, priority: number, description: string) {
		this.start_time = start_time;
		this.end_time = end_time;
		this.duration = duration;
		this.location_lat = location_lat;
		this.location_lng = location_lng;
		this.priority = priority;
		this.description = description;
	}
}

interface RouteTimeRequestBody {
	allTasks: number[];
	userLocation: LatLng;
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

/***************************************************
 * Find optimal route
 ***************************************************/

/**
 * Find a viable sequence of tasks that yields the lowest total time cost.
 *
 * @param tasksArr - Array of Task objects (NOT including "current location")
 * @param taskDistanceGraph - Pairwise distances matrix. The 0th row/col is for "current location"
 * @returns [sequenceOfTasks, totalTimeCost], or [[], -1] if none.
 *
 * If multiple sequences have the same time cost, it arbitrarily picks one.
 */
function findOptimalRoute(tasksArr: Task[], taskDistanceGraph: number[][]): [number[], number] {
	// tasksArr has length N, we label them 1..N in the distance graph
	const tasksSet: Set<number> = new Set(
		Array.from({ length: tasksArr.length }, (_, i) => i + 1)
	);
	const startTimeStr = "09:00";
	let timeCounter = timeToMinutes(startTimeStr);

	// "resultTracking" will hold multiple possible results
	// each entry is [timeCost, sequence[]]
	const resultTracking: Array<[number, number[]]> = [];

	// First: verify if all tasks are reachable at all from the current location
	for (let i = 1; i < taskDistanceGraph.length; i++) {
		const e_0i = taskDistanceGraph[0][i];
		if (timeCounter + e_0i + Math.max(0, tasksArr[i - 1].start_time - (timeCounter + e_0i)) > tasksArr[i - 1].end_time) {
			return [[], -1];
		}
	}

	// Explore all possible first-tasks
	for (let i = 1; i < taskDistanceGraph.length; i++) {
		const e_0i = taskDistanceGraph[0][i];
		const waitingTime = Math.max(0, tasksArr[i - 1].start_time - (timeCounter + e_0i));
		const timeCost = e_0i + tasksArr[i - 1].duration + waitingTime;

		const unfinishedTaskSet = new Set(tasksSet);
		unfinishedTaskSet.delete(i);

		findOptimalRouteHelper(tasksArr, taskDistanceGraph, unfinishedTaskSet, [i], timeCounter + timeCost, timeCost, resultTracking);
	}

	if (resultTracking.length === 0) {
		return [[], -1];
	}

	// find the sequence with the minimal time cost
	let selectedIndex = 0;
	let minTimeCost = resultTracking[0][0];

	for (let i = 1; i < resultTracking.length; i++) {
		if (resultTracking[i][0] < minTimeCost) {
			minTimeCost = resultTracking[i][0];
			selectedIndex = i;
		}
	}

	const bestRoute = resultTracking[selectedIndex][1]; // the sequence
	const bestTimeCost = resultTracking[selectedIndex][0];
	return [bestRoute, bestTimeCost];
}

/**
 * Recursive helper that explores all ways to schedule the remaining tasks.
 *
 * @param tasksArr
 * @param taskDistanceGraph
 * @param unfinishedTaskSet - set of tasks not yet done
 * @param finishedSequenceArr - array of tasks completed so far
 * @param timeCounter - "current clock time" in minutes from midnight
 * @param currTimeCost - total cost so far
 * @param resultTracking - accumulates all viable schedules
 */
function findOptimalRouteHelper(
	tasksArr: Task[],
	taskDistanceGraph: number[][],
	unfinishedTaskSet: Set<number>,
	finishedSequenceArr: number[],
	timeCounter: number,
	currTimeCost: number,
	resultTracking: Array<[number, number[]]>
): void {
	const lastTask = finishedSequenceArr[finishedSequenceArr.length - 1];

	// Check if all remaining tasks are still reachable from the last task
	for (const j of unfinishedTaskSet) {
		const e_ij = taskDistanceGraph[lastTask][j];
		const arrivalTime = timeCounter + e_ij;
		const waitTime = Math.max(0, tasksArr[j - 1].start_time - arrivalTime);
		if (arrivalTime + waitTime > tasksArr[j - 1].end_time) {
			return;
		}
	}

	// Explore next possible tasks
	for (const j of unfinishedTaskSet) {
		const e_ij = taskDistanceGraph[lastTask][j];
		const waitTime = Math.max(0, tasksArr[j - 1].start_time - (timeCounter + e_ij));
		const timeCost = e_ij + tasksArr[j - 1].duration + waitTime;

		const nextUnfinishedTaskSet = new Set(unfinishedTaskSet);
		nextUnfinishedTaskSet.delete(j);
		const nextFinishedSequenceArr = [...finishedSequenceArr, j];

		// If we've finished all tasks, record the result and stop recursion
		if (nextFinishedSequenceArr.length === tasksArr.length) {
			resultTracking.push([currTimeCost + timeCost, nextFinishedSequenceArr]);
			return;
		}

		// Otherwise, recurse further
		findOptimalRouteHelper(tasksArr, taskDistanceGraph, nextUnfinishedTaskSet, nextFinishedSequenceArr, timeCounter + timeCost, currTimeCost + timeCost, resultTracking);
	}
}

/**
 * Convert a 24-hour format time string (HH:MM) to absolute minutes since midnight.
 */
function timeToMinutes(timeStr: string): number {
	const [hours, minutes] = timeStr.split(":").map((v) => parseInt(v, 10));
	return hours * 60 + minutes;
}

/**
 * Calls the Google Distance Matrix API to get time distance between 2 points.
 */
async function fetch2TaskRouteTime(originTask: Task, destinationTask: Task): Promise<any> {
	const { default: fetch } = await import('node-fetch');

	const url = `https://maps.googleapis.com/maps/api/distancematrix/json?` +
		`&destinations=${destinationTask.location_lat}, ${destinationTask.location_lng}` +
		`&origins=${originTask.location_lat}, ${originTask.location_lng}` +
		`&key=${GMap_API_key}`;

	const response = await fetch(url);
	if (!response.ok) {
		throw new Error(`Route Request Failed: ${response.statusText}`);
	}
	const jsonResponse = await response.json();
	// console.log("jsonResponse:", jsonResponse);
	const routeTime = parse2TaskRouteTime(jsonResponse);
	// console.log("CompactJson:", compactJson);
	return routeTime;
}

// TODO: maybe should do error check
function parse2TaskRouteTime(jsonData: any): any {
	return jsonData.rows[0].elements[0].duration.value;
}


// Alternative request formate to make fewer requests

/**
 * Calls the Google Distance Matrix API and processes the response.
 */

async function fetchAllTaskRouteTime(allTask: Task[], userLocation: LatLng): Promise<any> {
	const { default: fetch } = await import('node-fetch');

	const url = buildURL(allTask, userLocation)

	const response = await fetch(url);
	if (!response.ok) {
		throw new Error(`Route Request Failed: ${response.statusText}`);
	}
	const jsonResponse = await response.json();
	// console.log("jsonResponse:", jsonResponse);
	const compactJson = parseAllTaskRouteTime(jsonResponse);
	// console.log("CompactJson:", compactJson);
	return compactJson;
}

function buildURL(allTask: Task[], userLocation: LatLng): any {

	// TODO: must include user location too!!

	// Convert this array into the parameter string required by the Distance Matrix API.
	const destinationsParam = allTask
		.map(task => `${task.location_lat},${task.location_lng}`)
		.join('|');

	const originsParam = allTask
		.map(task => `${task.location_lat},${task.location_lng}`)
		.join('|');

	// Construct the final URL
	const GMap_API_key = 'YOUR_API_KEY';
	const url = `https://maps.googleapis.com/maps/api/distancematrix/json?` +
		`origins=${originsParam}&destinations=${destinationsParam}` +
		`&key=${GMap_API_key}`;

	return url;
}


function parseAllTaskRouteTime(jsonData: any): any {
	for (const row of jsonData.rows) {
		for (const element of row.elements) {
			// Access the main duration
			console.log("Duration value:", element.duration.value); // e.g. 1620
		}
	}
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
		const { _id, name, start_time, end_time, duration, location_lat, location_lng, priority, description } = req.body
		if (!_id) { // add new task
			var new_task_id = dbService.addTask(name, start_time, end_time, duration, location_lat, location_lng, priority, description)
			res.status(200).json({
				"new_task_id": new_task_id
			});
		}
		else { // modify existing task
			var task_id = await dbService.modifyTask(_id, name, start_time, end_time, duration, location_lat, location_lng, priority, description)
			if (task_id == _id) {
				res.status(200).json({
					"new_task_id": task_id
				});
			} else {
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

// Another endpoint for findOptimalRoute
// user lat, lng, task array
app.post('/fetchOptimalRoute', async (req: Request<{}, any, RouteTimeRequestBody>, res: Response): Promise<void> => {
	try {
		const { allTasks, userLocation } = req.body;
		if (allTasks.length == 0 || !userLocation) {
			res.status(400).json({ error: 'Missing origin or destination coordinates.' });
			return;
		}
		console.log(`Received fetchRoute`);

		//TODO: need to query all the tasks first from data base
		
		//const graph_matrix = await fetchAllTaskRouteTime(allTasks, userLocation);
		//const result = findOptimalRoute(allTasks, graph_matrix)
		//res.json(result);
	} catch (error: any) {
		console.error(error);
		res.status(500).json({ error: error.message });
	}
});


app.listen(PORT, () => {
	console.log(`Server is running on port ${PORT}`);
});
