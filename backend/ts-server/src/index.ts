import express, { Request, Response } from 'express';
import fetch from 'node-fetch';
import { connectDB } from '../../../database/mongodb-ts/database';
import * as dbService from "../../../database/mongodb-ts/userService";
import { Console } from 'console';
import { Client } from "@googlemaps/google-maps-services-js";
import { fetchGeofences } from './geofenceService';
import { fetchAllTaskRouteTime, findOptimalRoute, timeToMinutes } from './fetchRouteService';
import dotenv from 'dotenv';
import path from 'path';
dotenv.config({ path: path.resolve(__dirname, '.env') });


const app = express();
const PORT = process.env.PORT || 3000;
const GMap_API_key = process.env.GMap_API_key
const db = connectDB();
console.log("Connected to MongoDB");
console.log("GMap_API_key:" + GMap_API_key)

// npx nodemon --exec ts-node src/index.ts
app.use(express.json());

app.get('/', (req, res) => {
	res.send('Hello from your TypeScript server!');
});

interface LatLng {
	latitude: number;
	longitude: number;
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

export class Task {
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
			res.status(200).json({
				"new_task_id": task_id
			});
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
		if (allTasksID.length == 0 
			|| !userLocation 
			|| !userCurrTime 
			|| (userLocation.latitude == 0 && userLocation.longitude == 0)) {
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
		//console.log(allTasks); normal
		const graph_matrix = await fetchAllTaskRouteTime(allTasks, userLocation);
		console.log("fetch route info done");
		console.log(graph_matrix);
		const result = findOptimalRoute(allTasks, graph_matrix, userCurrTime);
		console.log("find optimal route done")
		const taskIds: string[] = result[0].map(task_i => {
			/*
			//impossible case
			if (task_i < 0 || task_i > allTasks.length) {
				console.error(`Invalid index: ${task_i}, allTasks length: ${allTasks.length}`);
				return null; 
			}
			*/
			return allTasks[task_i-1]._id;
		}).filter(id => id !== null);
		console.log(`[Optimal Route] found: ${taskIds}`);
		res.json({"taskIds": taskIds, "time_cost": result[1]});	
	} catch (error: any) {
		//console.error(error);
		res.status(500).json({ error: error.message });
	}
});


if (process.env.NODE_ENV !== "test") {
    app.listen(PORT, () => {
        console.log(`Server is running on port ${PORT}`);
    });
}



export {app, db};