import { Request, Response } from 'express';
import * as dbService from '../../../../database/mongodb-ts/userService';
import { AddTaskRequestBody } from '../index';

export const addTask =  async (req: Request<{}, any, AddTaskRequestBody>, res: Response): Promise<void> => {
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
};

export const getAllTasks =  async (req: Request, res: Response): Promise<void> => {
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
};


export const deleteTask = async (req: Request<{}, any, {owner_id: string, _id: string }>, res: Response): Promise<void> => {
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
};
