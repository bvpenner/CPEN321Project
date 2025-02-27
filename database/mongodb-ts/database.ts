import { MongoClient } from "mongodb";
import dotenv from "dotenv";

dotenv.config();

const Mongo_URL = "mongodb://localhost:27017/mydatabase"
const client = new MongoClient(Mongo_URL);

async function connectDB() {
    try {
        await client.connect();
        return client.db(); 
    } catch (error) {
        console.error("MongoDB connection error:", error);
        process.exit(1);
    }
}

export { connectDB, client };
