import { Request, Response } from 'express';
import * as dbService from '../../../../database/mongodb-ts/userService';
import { Task, RouteTimeRequestBody } from '../index';
import { fetchAllTaskRouteTime } from '../fetchRouteService';


export const fetchOptimalRoute = async (req: Request<{}, any, RouteTimeRequestBody>, res: Response): Promise<void> => {
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
		
		const graph_matrix = await fetchAllTaskRouteTime(allTasks, userLocation);
		const result = findOptimalRoute(allTasks, graph_matrix, userCurrTime);
	
		const taskIds: string[] = result[0].map(task_i => {

			return allTasks[task_i-1]._id;
		}).filter(id => id !== null);
		console.log(`[Optimal Route] found: ${taskIds}`);
		res.json({"taskIds": taskIds, "time_cost": result[1]});	
	} catch (error: any) {
		res.status(500).json({ error: error.message });
	}
};

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