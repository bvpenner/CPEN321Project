import fetch from 'node-fetch';
import { Client } from "@googlemaps/google-maps-services-js";
import { Task } from "./index";
import dotenv from 'dotenv';
import path from 'path';
dotenv.config({ path: path.resolve(__dirname, '.env') });

const googleMapsClient = new Client({});        
const GMap_API_key = process.env.GMap_API_key

interface LatLng {
	latitude: number;
	longitude: number;
}


/**
 * Calls the Google Distance Matrix API with a list of task locations.
 * Alternative request format to make fewer requests
 */
async function fetchAllTaskRouteTime(allTask: Task[], userLocation: LatLng): Promise<any> {
    const { default: fetch } = await import('node-fetch');

    const url = buildURL(allTask, userLocation)

    const response = await fetch(url);
    const jsonResponse = await response.json();

    const timeDistanceMatrix = parseAllTaskRouteTime(jsonResponse);
    return timeDistanceMatrix;
}

function buildURL(allTask: Task[], userLocation: LatLng): any {
    // Convert this array into the parameter string required by the Distance Matrix API.
    const destinationsParam = allTask
        .map(task => `${task.location_lat},${task.location_lng}`)
        .join('|');

    const allDestinationsParam = `${userLocation.latitude},${userLocation.longitude}|` + destinationsParam;
    
    const originsParam = allTask
        .map(task => `${task.location_lat},${task.location_lng}`)
        .join('|');

    const allOriginsParam = `${userLocation.latitude},${userLocation.longitude}|` + originsParam;

    const url = `https://maps.googleapis.com/maps/api/distancematrix/json?` +
        `origins=${allOriginsParam}&destinations=${allDestinationsParam}` +
        `&key=${GMap_API_key}`;

    return url;
}

function parseAllTaskRouteTime(jsonData: any): any {
    const durationsMatrix: number[][] = []; 

    //each row is one origin to each destinations
    for (let i = 0; i < jsonData.rows.length; i++) {
        const row = jsonData.rows[i];
        const durationRow: number[] = []; 

        for (let j = 0; j < row.elements.length; j++) {
            const element = row.elements[j];
            durationRow.push(element.duration.value / 60); // Extracting duration value, original in second
        }

        durationsMatrix.push(durationRow);
    }

    return durationsMatrix
}

export {fetchAllTaskRouteTime}