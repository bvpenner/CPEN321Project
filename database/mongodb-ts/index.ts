import { addUser, getUsers, deleteUserByName } from "./userService";

// npx ts-node index.ts

async function main() {
    await addUser("Alice", "alice@example.com");
    // await addUser({ name: "Billy", email: "billy@example.com", age: 25 });
    await deleteUserByName("Billy");

    const users = await getUsers();
    console.log(users);
}

main();
