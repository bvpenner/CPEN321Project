import { addUser, getUsers, deleteUserByName, getTasks, getUserTasks, getAllTasksInList, DBDeleteAll} from "./userService";
import { updateTaskStartDates} from "./userService";

// npx ts-node index.ts

async function loguser() {
    // await addUser("Alice", "alice@example.com");
    // await addUser({ name: "Billy", email: "billy@example.com", age: 25 });
    // await deleteUserByName("Billy");

    const users = await getUsers();

    const task_id_list = await getUserTasks("103042323293350711668")
    console.log(users);
    console.log(task_id_list);

    const task_list = await getAllTasksInList(task_id_list)
    console.log(task_list);
}

async function logTask() {
    const users = await getTasks();
    console.log(users);
}

async function DeleteAll() {
    const users = await DBDeleteAll();
    console.log(users);
}




// loguser();
// updateTaskStartDates();
logTask();

// DeleteAll();
