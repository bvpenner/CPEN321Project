import request from "supertest";
import { app } from "../backend/ts-server/src/index";

// npx jest nonfunctional.test.ts --coverage --runInBand

const validUID = "test_id_backend";
const valid_username = "Test Dummy";
const valid_useremail = "TestDummy@gmail.com";

describe("Scalability Test - API Response Time", () => {
    test("API should respond within 200ms", async () => {
        const response = await request(app)
            .get("/getAllTasks")
            .query({ u_id: validUID });
        
        expect(response.status).toBe(200); 
        expect(response.body).toHaveProperty("task_list");
        expect(Array.isArray(response.body.task_list)).toBe(true);
    }, 5000);
});


describe("Notification Accuracy - HTTP Response Time", () => {
    test("Notification API should respond within 2s", async () => {
        const start = performance.now();

        const response = await request(app)
            .get("/getAllTasks")
            .query({ u_id: validUID });
        const duration = performance.now() - start;

        console.log(`HTTP Notification Response Time: ${duration.toFixed(2)} ms`);
        expect(response.status).toBe(200);
        expect(duration).toBeLessThan(2000);
    }, 5000);
});