import express, { Request, Response } from 'express';
import { connectDB } from '../../../database/mongodb-ts/database';
import taskRoutes from './routes/taskRoutes';
import loginRoute from './routes/loginRoute';
import geofencesRoute from './routes/geofencesRoute';
import optimalRouteRoute from './routes/optimalRouteRoute';
import https from 'https';
import fs from 'fs';

import dotenv from 'dotenv';
import path from 'path';
dotenv.config({ path: path.resolve(__dirname, '.env') });

const app = express();
const PORT = process.env.PORT || 3000;
const GMap_API_key = process.env.GMap_API_key
const db = connectDB();
console.log("Connected to MongoDB");
console.log("GMap_API_key:" + GMap_API_key)

const options = {
	key: fs.readFileSync(path.join(__dirname, 'key.pem')),
	cert: fs.readFileSync(path.join(__dirname, 'cert.pem'))
};

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
export interface RouteRequestBody {
	origin: LatLng;
	destination: LatLng;
}

export interface AddTaskRequestBody {
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

export interface RouteTimeRequestBody {
	allTasksID: number[];
	userLocation: LatLng;
	userCurrTime: string;
}

app.use('/', taskRoutes);
app.use('/', loginRoute);
app.use('/', geofencesRoute);
app.use('/', optimalRouteRoute);

if (process.env.NODE_ENV !== "test") {
    app.listen(PORT, () => {
        console.log(`Server is running on port ${PORT}`);
    });
}

https.createServer(options, app).listen(3001, () => {
	console.log('HTTPS server running on port 3001');
});


export {app, db};