import fetch from 'node-fetch';
import { Client } from "@googlemaps/google-maps-services-js";

const googleMapsClient = new Client({});        //unsure about this
const GMap_API_key = "AIzaSyBoG58gmt5sB4p6dmwZBz40Doa_xn8zkks"

interface LatLng {
	latitude: number;
	longitude: number;
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

export {fetchAllTaskRouteTime}