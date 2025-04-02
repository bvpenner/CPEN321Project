import fetch from 'node-fetch';
import { Client } from "@googlemaps/google-maps-services-js";
import { Task } from "./index";

const googleMapsClient = new Client({});        //unsure about this
const GMap_API_key = "AIzaSyBoG58gmt5sB4p6dmwZBz40Doa_xn8zkks"

interface LatLng {
	latitude: number;
	longitude: number;
}




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

    // Explore all possible first-taskss
    for (let i = 1; i < taskDistanceGraph.length; i++) {
        const e_0i = taskDistanceGraph[0][i];
        const waitingTime = Math.max(0, tasksArr[i - 1].start_time - (timeCounter + e_0i));
        const timeCost = e_0i + tasksArr[i - 1].duration + waitingTime;

        const unfinishedTaskSet = new Set(tasksSet);
        unfinishedTaskSet.delete(i);

        //added for length == 1 case
        if (unfinishedTaskSet.size === 0) {
            resultTracking.push([timeCost, [1]]);
            break;
        }
        findOptimalRouteHelper(tasksArr, taskDistanceGraph, unfinishedTaskSet, [i], timeCounter + timeCost, timeCost, resultTracking);
    }
    
    if (resultTracking.length === 0) {
        return [[], -1];
    }

    console.log("possible route time computation completed")


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
// async function fetch2TaskRouteTime(originTask: Task, destinationTask: Task): Promise<any> {
// 	const { default: fetch } = await import('node-fetch');

// 	const url = `https://maps.googleapis.com/maps/api/distancematrix/json?` +
// 		`&destinations=${destinationTask.location_lat}, ${destinationTask.location_lng}` +
// 		`&origins=${originTask.location_lat}, ${originTask.location_lng}` +
// 		`&key=${GMap_API_key}`;

// 	const response = await fetch(url);
// 	if (!response.ok) {
// 		throw new Error(`Route Request Failed: ${response.statusText}`);
// 	}
// 	const jsonResponse = await response.json();
// 	// console.log("jsonResponse:", jsonResponse);
// 	const routeTime = parse2TaskRouteTime(jsonResponse);
// 	// console.log("CompactJson:", compactJson);
// 	return routeTime;
// }

// TODO: maybe should do error check
// function parse2TaskRouteTime(jsonData: any): any {
// 	return jsonData.rows[0].elements[0].duration.value;
// }


/**
 * Calls the Google Distance Matrix API with a list of task locations.
 * Alternative request format to make fewer requests
 */
async function fetchAllTaskRouteTime(allTask: Task[], userLocation: LatLng): Promise<any> {
    const { default: fetch } = await import('node-fetch');

    const url = buildURL(allTask, userLocation)

    const response = await fetch(url);
    console.log("examine distance matrix response status");
    console.log(response.status);
    
    const jsonResponse = await response.json();
    console.log(jsonResponse);
    console.log(jsonResponse.status);
    /*
    if(jsonResponse.status != "OK"){
        throw new Error(jsonResponse.error_message);
    }
    */

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
            //examine status
            console.log(element.status)
            durationRow.push(element.duration.value / 60); // Extracting duration value, original in second
        }

        durationsMatrix.push(durationRow);
    }

    // Print the 2D matrix
    // console.log("durationsMatrix");
    // console.log(durationsMatrix);
    return durationsMatrix
}

export {fetchAllTaskRouteTime, findOptimalRoute, timeToMinutes}