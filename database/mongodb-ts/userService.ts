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

async function addUser(name: string, email: string): Promise<void> {
    const db = await connectDB();
    const usersCollection: Collection<User> = db.collection("users");
    const newUser: User = {
        _id: uuidv4(),
        name: name,
        email: email,
        tasks_list: ["task1_id_test", "task2_id_test", "task3_id_test"]
    };
    await usersCollection.insertOne(newUser);
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
    const usersCollection: Collection<Task> = db.collection("tasks");
    return usersCollection.find().toArray();
}

async function addTask(name: string, start: string, end: string, duration: number, location_lat: number, location_lng: number, priority: number, description: string): Promise<string> {
    const db = await connectDB();
    const usersCollection: Collection<Task> = db.collection("tasks");

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
    await usersCollection.insertOne(newTask);

    return new_task_id;
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
        const usersCollection = db.collection<Task>("tasks");

        const result = await usersCollection.deleteOne({ _id: id });

        if (result.deletedCount === 0) {
            console.log(`Task with id "${id}" not found.`);
        } else {
            console.log(`Task with id  "${id}" deleted successfully.`);
        }
    } catch (error) {
        console.error("Error deleting Task:", error);
    }
}


export { getUsers, addUser, deleteUserByName, addTask, getTasks, deleteTaskById, modifyTask};
