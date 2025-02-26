import { Collection } from "mongodb";
import { connectDB } from "./database";
import { User } from "./models/User";
import { Task } from "./models/Task";
import { ObjectId } from "mongodb";
import { v4 as uuidv4 } from "uuid";

// USER Management
async function getUsers(): Promise<User[]> {
    const db = await connectDB();
    const usersCollection: Collection<User> = db.collection("users");
    return usersCollection.find().toArray();
}

async function addUser(name: string, email: string): Promise<string> {
    const db = await connectDB();
    const usersCollection: Collection<User> = db.collection("users");
    const new_user_id = uuidv4()
    
    const newUser: User = {
        _id: new_user_id,
        name: name,
        email: email,
        tasks_list: []
    };
    await usersCollection.insertOne(newUser);

    return new_user_id;
}

async function deleteUserByName(name: string): Promise<void> {
    try {
        const db = await connectDB();
        const usersCollection = db.collection<User>("users");

        const result = await usersCollection.deleteMany({ name });

        if (result.deletedCount === 0) {
            console.log(`User with name "${name}" not found.`);
        } else {
            console.log(`User with name "${name}" deleted successfully.`);
        }
    } catch (error) {
        console.error("Error deleting user:", error);
    }
}

// TASK Management
async function getTasks(): Promise<Task[]> {
    const db = await connectDB();
    const tasksCollection: Collection<Task> = db.collection("tasks");
    return tasksCollection.find().toArray();
}

async function getTasksById(_id: string): Promise<Task> {
    const db = await connectDB();
    const tasksCollection: Collection<Task> = db.collection("tasks");

    const found_tasks  = await tasksCollection.findOne({ _id: _id });
    if (!found_tasks) {
        throw new Error(`[Error] Task with id ${_id} not found`);
    }
    return found_tasks;
}

async function addTask(name: string, start: string, end: string, duration: number, location_lat: number, location_lng: number, priority: number, description: string): Promise<string> {
    const db = await connectDB();
    const tasksCollection: Collection<Task> = db.collection("tasks");

    const new_task_id = uuidv4()
    const newTask: Task = {
        _id: new_task_id,
        name: name,
        start: start, 
        end: end, 
        duration: duration,
        location_lat: location_lat, 
        location_lng: location_lng, 
        priority: priority, // 1 = High, 2 = Medium, 3 = Low
        description: description
    };
    await tasksCollection.insertOne(newTask);

    return new_task_id;
}

async function addTaskToUser(user_id: string, task_id: string): Promise<void> {
    const db = await connectDB();
    const usersCollection: Collection<Task> = db.collection("users");
    const found_user = await usersCollection.findOne({ _id: user_id });
    
    if (!found_user) {
        throw new Error(`User with id ${user_id} not found`);
    }

    await usersCollection.updateOne(
        { _id: user_id }, 
        { $addToSet: { tasks_list: task_id } }
    );
}


async function modifyTask(
    _id: string,
    name: string,
    start: string,
    end: string,
    duration: number,
    location_lat: number,
    location_lng: number,
    priority: number,
    description: string
): Promise<string> {
    const db = await connectDB();
    const tasksCollection: Collection<Task> = db.collection("tasks");

    try {
        const existingTask = await tasksCollection.findOne({ _id: _id });

        if (!existingTask) {
            console.log(`[Error] Task not found with _id: ${_id}`);
            return "Task not found";
        }

        // Update the task
        await tasksCollection.updateOne(
            { _id: _id },
            {
                $set: {
                    name,
                    start,
                    end,
                    duration,
                    location_lat,
                    location_lng,
                    priority,
                    description,
                },
            }
        );

        console.log(`Updated task: ${_id}`);
        return _id; 
    } catch (error) {
        console.error("Error updating task:", error);
        return "Error updating task";
    }
}



async function deleteTaskById(id: string): Promise<void> {
    try {
        const db = await connectDB();
        const tasksCollection = db.collection<Task>("tasks");

        const result = await tasksCollection.deleteOne({ _id: id });

        if (result.deletedCount === 0) {
            console.log(`Task with id "${id}" not found.`);
        } else {
            console.log(`Task with id  "${id}" deleted successfully.`);
        }
    } catch (error) {
        console.error("Error deleting Task:", error);
    }
}


export { getUsers, addUser, deleteUserByName, addTask, getTasks, deleteTaskById, modifyTask, getTasksById, addTaskToUser};
