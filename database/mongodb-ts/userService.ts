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

async function addUser(u_id: string, name: string, email: string): Promise<string> {
    const db = await connectDB();
    const usersCollection: Collection<User> = db.collection("users");
    
    const existingUser = await usersCollection.findOne({ _id: u_id });

    if (existingUser) {
        return u_id; // User already exists, return the same `u_id`
    }

    // If not found, create a new user
    const newUser: User = {
        _id: u_id,
        name: name,
        email: email,
        tasks_list: []
    };

    await usersCollection.insertOne(newUser);
    return u_id;
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

async function getUserTasks(u_id: string): Promise<string[]> {
    const db = await connectDB();
    const usersCollection: Collection<User> = db.collection("users");

    // Find the user by `_id`
    const user = await usersCollection.findOne({ _id: u_id });

    if (!user) {
        console.log(`User with ID ${u_id} not found`);
        return [];
    }

    return user.tasks_list;
}


async function getAllTasksInList(id_list: string[]): Promise<Task[]> {
    const db = await connectDB();
    const tasksCollection: Collection<Task> = db.collection("tasks");

    const tasks = await tasksCollection.find({ _id: { $in: id_list } }).toArray();

    return tasks;
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

async function DBDeleteAll(): Promise<void> {
    try {
        const db = await connectDB();
        const tasksCollection = db.collection<Task>("tasks");
        const usersCollection: Collection<User> = db.collection("users");

        const result_task = await tasksCollection.deleteMany({});
        const result_user = await usersCollection.deleteMany({});

        console.log(`Deleted ${result_task.deletedCount} tasks from the collection.`);
        console.log(`Deleted ${result_user.deletedCount} tasks from the collection.`);
    } catch (error) {
        console.error("Error deleting all tasks:", error);
    }
}

/////////////////////////////Test//////////////////////////////////
async function updateTaskStartDates(): Promise<void> {
    const db = await connectDB();
    const tasksCollection: Collection<Task> = db.collection("tasks");

    // Fetch first 3 tasks
    const tasks = await tasksCollection.find().limit(3).toArray();

    if (tasks.length < 3) {
        throw new Error("[Error] Not enough tasks to update.");
    }

    const newStartDates = ["14:00", "14:00", "14:00"];

    for (let i = 0; i < 3; i++) {
        await tasksCollection.updateOne(
            { _id: tasks[i]._id },
            { $set: { end: newStartDates[i] } }
        );
        console.log(`Updated Task ${tasks[i]._id} to start on ${newStartDates[i]}`);
    }
}

export { getUsers, addUser, deleteUserByName, addTask, getTasks, deleteTaskById, modifyTask, getTasksById, addTaskToUser, getUserTasks,
    getAllTasksInList, DBDeleteAll,
    updateTaskStartDates
};
