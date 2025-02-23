import { MongoClient } from "mongodb";
import dotenv from "dotenv";

dotenv.config();

const client = new MongoClient(process.env.MONGO_URI!);

async function connectDB() {
    try {
        await client.connect();
        console.log("Connected to MongoDB");
        return client.db(); 
    } catch (error) {
        console.error("MongoDB connection error:", error);
        process.exit(1);
    }
}

export { connectDB, client };
