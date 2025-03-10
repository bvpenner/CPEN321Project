import express, { Request, Response } from 'express';
import fetch from 'node-fetch';
import { connectDB } from '../../database/mongodb-ts/database';
import * as dbService from "../../database/mongodb-ts/userService";
import { Console } from 'console';
import { Client } from "@googlemaps/google-maps-services-js";
import { fetchGeofences } from './geofenceService';


const app = express();
const PORT = process.env.PORT || 3000;
const GMap_API_key = "AIzaSyBoG58gmt5sB4p6dmwZBz40Doa_xn8zkks"
const db = connectDB();
console.log("Connected to MongoDB");

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


// Request body interface for /fetchGeofences endpoint
interface RouteRequestBody {
	origin: LatLng;
	destination: LatLng;
}

interface AddTaskRequestBody {
	owner_id: string;
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
	public _id: string;
	public start_time: number;
	public end_time: number;    // latest time to reach a task
	public duration: number;    // in minutes
	public location_lat: number;
	public location_lng: number;
	public priority: number;
	public description: string;

	constructor(_id: string, start_time: number, end_time: number, duration: number, location_lat: number, location_lng: number, priority: number, description: string) {
		this._id = _id;
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
	allTasksID: number[];
	userLocation: LatLng;
	userCurrTime: string;
}

/**
 * Calls the Google Directions API and processes the response.
 */






/***************************************************
 * Find optimal route
 ***************************************************/

/**
 * Find a viable sequence of tasks that yields the lowest total time cost.
 *
 * @param tasksArr - Array of Task objects (NOT including "current location")
 * @param taskDistanceGraph - Pairwise time distances matrix. The 0th row/col is for "current location"
 * @param userCurrTime - in 24h fromat
 * @returns [sequenceOfTasks, totalTimeCost], or [[], -1] if none.
 *
 * If multiple sequences have the same time cost, it arbitrarily picks one.
 */
function findOptimalRoute(tasksArr: Task[], taskDistanceGraph: number[][], userCurrTime: string): [number[], number] {
	// tasksArr has length N, we label them 1..N in the distance graph
	const tasksSet: Set<number> = new Set(
		Array.from({ length: tasksArr.length }, (_, i) => i + 1)
	);
	const startTimeStr = userCurrTime;
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


/**
 * Calls the Google Distance Matrix API with a list of task locations.
 * Alternative request format to make fewer requests
 */
async function fetchAllTaskRouteTime(allTask: Task[], userLocation: LatLng): Promise<any> {
	const { default: fetch } = await import('node-fetch');

	const url = buildURL(allTask, userLocation)

	const response = await fetch(url);
	if (!response.ok) {
		throw new Error(`Route Time Request Failed: ${response.statusText}`);
	}
	const jsonResponse = await response.json();
	const timeDistanceMatrix = parseAllTaskRouteTime(jsonResponse);
	// console.log("CompactJson:", jsonResponse);
	return timeDistanceMatrix;
}

function buildURL(allTask: Task[], userLocation: LatLng): any {
	// Convert this array into the parameter string required by the Distance Matrix API.
	const destinationsParam = allTask
		.map(task => `${task.location_lat},${task.location_lng}`)
		.join('|');

	//include user location!!
	const allDestinationsParam = `${userLocation.latitude},${userLocation.longitude}|` + destinationsParam;
	// console.log(allDestinationsParam)
	const originsParam = allTask
		.map(task => `${task.location_lat},${task.location_lng}`)
		.join('|');

	const allOriginsParam = `${userLocation.latitude},${userLocation.longitude}|` + originsParam;

	// Construct the final URL
	const url = `https://maps.googleapis.com/maps/api/distancematrix/json?` +
		`origins=${allOriginsParam}&destinations=${allDestinationsParam}` +
		`&key=${GMap_API_key}`;

	return url;
}

// TODO: to be verified (logic)!!!
function parseAllTaskRouteTime(jsonData: any): any {
	const durationsMatrix: number[][] = []; // 2D matrix to store durations

	//each row is one origin to each destinations
	for (let i = 0; i < jsonData.rows.length; i++) {
		const row = jsonData.rows[i];
		const durationRow: number[] = []; // Store durations for this row

		for (let j = 0; j < row.elements.length; j++) {
			const element = row.elements[j];
			durationRow.push(element.duration.value / 60); // Extracting duration value, original in second
		}

		durationsMatrix.push(durationRow);
	}

	// Print the 2D matrix
	// console.log("durationsMatrix");
	// console.log(durationsMatrix);
	return durationsMatrix
}


// -------------------------
// API Endpoint Definition
// -------------------------

app.post('/fetchGeofences', async (req: Request<{}, any, RouteRequestBody>, res: Response): Promise<void> => {
	try {
		const { origin, destination } = req.body;
		if (!origin || !destination) {
			res.status(400).json({ error: 'Missing origin or destination coordinates.' });
			return;
		}
		const result = await fetchGeofences(origin, destination);
		console.log("fetchGeofences result: " + result)
		res.json(result);
	} catch (error: any) {
		// console.error(error);
		res.status(500).json({ error: error.message });
	}
});

app.post('/login', async (req: Request<{}, any, {u_id:string, name: string, email: string}>, res: Response): Promise<void> => {
	try {
		const {u_id, name, email } = req.body
		if (!name || !email || !u_id) {
			throw new Error("Missing or invalid _id in request body");
		}
		
		var new_user_id = await dbService.addUser(u_id, name, email)

		console.log(`[Logged in] User: ${name} ${email} ${new_user_id[0]}`);
		res.status(200).json({
			"new_user_id": new_user_id[0],
			"is_new": new_user_id[1]
		});
	} catch (error: any) {
		// console.error(error);
		res.status(500).json({ error: error.message });
	}
})

app.post('/addTask', async (req: Request<{}, any, AddTaskRequestBody>, res: Response): Promise<void> => {
	try {
		const { owner_id, _id, name, start_time, end_time, duration, location_lat, location_lng, priority, description } = req.body

		console.log(`[Add Task] Received from ${owner_id}: ${_id}`)

		if (!_id) { // add new task
			var new_task_id = await dbService.addTask(name, start_time, end_time, duration, location_lat, location_lng, priority, description)
			dbService.addTaskToUser(owner_id, new_task_id);
			
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

// app.post('/getAllTasks', async (req: Request<{}, any, {u_id: string}>, res: Response): Promise<void> => {
// 	try {
// 		const { u_id } = req.body
// 		if (!u_id) {
// 			throw new Error("Missing or invalid _id in request body");
// 		}
// 		const task_id_list = await dbService.getUserTasks(u_id)
// 		const task_list = await dbService.getAllTasksInList(task_id_list)
		
// 		res.status(200).json({
// 			"task_list": task_list || []
// 		});
// 	} catch (error: any) {
// 		console.error(error);
// 		res.status(500).json({ error: error.message });
// 	}
// })

app.get('/getAllTasks', async (req: Request, res: Response): Promise<void> => {
	try {
		const { u_id } = req.query;
		console.log(`[GetAll Task] Received from ${u_id}}`)
		if (!u_id || typeof u_id !== 'string') {
			throw new Error("Missing or invalid u_id in request query");
		}
		const task_id_list = await dbService.getUserTasks(u_id);
		const task_list = await dbService.getAllTasksInList(task_id_list);

		res.status(200).json({
			"task_list": task_list || []
		});
	} catch (error: any) {
		// console.error(error);
		res.status(500).json({ error: error.message });
	}
});



app.post('/deleteTask', async (req: Request<{}, any, {owner_id: string, _id: string }>, res: Response): Promise<void> => {
	try {
		const {owner_id, _id} = req.body
		if (!_id) {
			throw new Error("Missing or invalid _id in request body");
		}
		console.log(`[Delete Task] Received from ${owner_id}: ${_id}`)
		var new_task_id = dbService.deleteTaskById(owner_id, _id)
		res.status(200).json({
			"new_task_id": new_task_id
		});

	} catch (error: any) {
		// console.error(error);
		res.status(500).json({ error: error.message });
	}
})


// Another endpoint for findOptimalRoute
// input: user lat, lng, task_id list
// return: a list of task_ids
app.post('/fetchOptimalRoute', async (req: Request<{}, any, RouteTimeRequestBody>, res: Response): Promise<void> => {
	try {
		const { allTasksID, userLocation, userCurrTime } = req.body;
		if (allTasksID.length == 0 || !userLocation || !userCurrTime) {
			res.status(400).json({ error: 'Missing origin or destination coordinates.' });
			return;
		}
		console.log(`[fetchOptimalRoute] Received: ${userLocation} | ${userCurrTime}`);

		// query all the tasks first from data base
		const allTasks: Task[] = []
		for(var i = 0; i < allTasksID.length; i++){
			const new_task = await dbService.getTasksById(`${allTasksID[i]}`);
			const task = new Task(new_task._id, timeToMinutes(new_task.start), timeToMinutes(new_task.end), new_task.duration, new_task.location_lat, new_task.location_lng, new_task.priority, new_task.description)
			allTasks.push(task);
		}
		// console.log(allTasks);
		const graph_matrix = await fetchAllTaskRouteTime(allTasks, userLocation);
		const result = findOptimalRoute(allTasks, graph_matrix, userCurrTime);
		// console.log(result)
		const taskIds: string[] = result[0].map(task_i => {
			if (task_i < 0 || task_i > allTasks.length) {
				console.error(`Invalid index: ${task_i}, allTasks length: ${allTasks.length}`);
				return null; 
			}
			return allTasks[task_i-1]._id;
		}).filter(id => id !== null);
		console.log(`[Optimal Route] found: ${taskIds}`);
		res.json({"taskIds": taskIds, "time_cost": result[1]});	
	} catch (error: any) {
		console.error(error);
		res.status(500).json({ error: error.message });
	}
});


if (process.env.NODE_ENV !== "test") {
    app.listen(PORT, () => {
        console.log(`Server is running on port ${PORT}`);
    });
}



export {app, db};