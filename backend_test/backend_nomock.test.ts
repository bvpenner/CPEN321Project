import request from "supertest";
import { app , db } from "../ts-server/src/index";
import { isStringObject } from "util/types";
import { ObjectId } from "mongodb";

/////////////////////////////////////////////////
// Without Mock
// npx jest backend_nomock.test.ts --preset=ts-jest
// npx jest backend_nomock.test.ts backend_mock.test.ts --coverage --runInBand
/////////////////////////////////////////////////

const validUID = "test_id_backend";
const valid_username = "Test Dummy";
const valid_useremail = "TestDummy@gmail.com";
var temp_taskid: ObjectId = new ObjectId(); 

describe("/ (No Mocks)", () => {
    test("test ping", async () => {
        const response = await request(app)
            .get("/");

        expect(response.status).toBe(200); 
    });
});

describe("/getAllTasks (No Mocks)", () => {
    test("should return user data from real API", async () => {
        const response = await request(app)
            .get("/getAllTasks")
            .query({ u_id: validUID });

        expect(response.status).toBe(200); 
        expect(response.body).toHaveProperty("task_list");
        expect(Array.isArray(response.body.task_list)).toBe(true);
    });
});

describe("/getAllTasks (No Mocks)", () => {
    test("should return 500 from missing u_id", async () => {
        const response = await request(app)
            .get("/getAllTasks")
            .query({ u_id: "" });

        expect(response.status).toBe(500); 
        expect(response.body).toHaveProperty("error");
    });
});

describe("/login (No Mocks)", () => {
    test("should return 200 for valid u_id", async () => {
        const response = await request(app)
            .post("/login")
            .send({ u_id: validUID , name: valid_username, email: valid_useremail })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_user_id");
        expect(response.body).toHaveProperty("is_new");
        expect(typeof response.body.new_user_id).toBe("string");
        expect(typeof response.body.is_new).toBe("string");
        expect(response.body.is_new).toBe("0"); // Existing User
    });
});

describe("/login (No Mocks)", () => {
    test("should return 500 for missing name", async () => {
        const response = await request(app)
            .post("/login")
            .send({ u_id: validUID, name: "", email: valid_useremail })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error");
    });
});

describe("/login (No Mocks)", () => {
    test("should return 500 for missing email", async () => {
        const response = await request(app)
            .post("/login")
            .send({ u_id: validUID, name: valid_username, email: "" })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error");
    });
});

describe("/login (No Mocks)", () => {
    test("should return 500 for missing u_id", async () => {
        const response = await request(app)
            .post("/login")
            .send({ u_id: "" , name: valid_username, email: valid_useremail })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error");
    });
});

describe("/addTask (No Mocks)", () => {
    test("should return 200 for success create new task", async () => {
        const response = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
                name: "test_task_3",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_task_id");
        expect(typeof response.body.new_task_id).toBe("string");
        temp_taskid = response.body.new_task_id
        console.log("temp_taskid: " + temp_taskid)
    });
});

describe("/addTask (No Mocks)", () => {
    test("should return 200 for success update task", async () => {
        const response = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid, 
                name: "test_task_1",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_task_id");
        expect(typeof response.body.new_task_id).toBe("string");
    });
});

describe("/deleteTask (No Mocks)", () => {
    test("should return 200 for success deleteTask", async () => {
        const response = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid, 
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_task_id");
    });
});

describe("/deleteTask (No Mocks)", () => {
    test("should return 500 for fail deleteTask (missing ID)", async () => {
        const response = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error");
    });
});


describe("/fetchGeofences (No Mocks)", () => {
    test("should return 200 for success response", async () => {
        const response = await request(app)
            .post("/fetchGeofences")
            .send({ 
                origin: {
                    latitude: 49.269139,
                    longitude: -123.215108
                }, 
                destination: {
                    latitude: 49.26913,
                    longitude: -123.215108
                }, 
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("polygon");
        expect(Array.isArray(response.body.polygon)).toBe(true);
    });
});


describe("/fetchGeofences (No Mocks)", () => {
    test("should return 400 for incomplete request", async () => {
        const response = await request(app)
            .post("/fetchGeofences")
            .send({ })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(400);
        expect(response.body).toHaveProperty("error");
    });
});

/////////////////////////////////////////////////
// FindOptimalRoute tests
/////////////////////////////////////////////////

describe("/findOptimalRoute (No Mocks)", () => {
    test("case 1: user selected no task", async () => {
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [],
                userLocation: {
                    latitude: 49.269139,
                    longitude: -123.215108
                }, 
                userCurrTime: "10:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(400);
    });

    test("case 2: user gives no time", async () => {
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [],
                userLocation: {
                    latitude: 49.269139,
                    longitude: -123.215108
                }, 
                userCurrTime: ""
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(400);
    });

    test("case 3: user gives no location", async () => {
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [],
                userLocation: {
                    latitude: 0,
                    longitude: 0
                }, 
                userCurrTime: "10:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(400);
    });

    test("case 4: user gives all required info, single tasks, viable route", async () => {
        
        //add new tasks
        //specific fields tbd
        var temp_taskid_1: ObjectId = new ObjectId(); 

        const task_1_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid_1, 
                name: "test_task_1",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");

        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [task_1_res.body.new_task_id],
                userLocation: {
                    latitude: 49.269139,
                    longitude: -123.215108
                }, 
                userCurrTime: "10:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        // more expect

        //clean up
        const cleanUpResponse_1 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: "temp_taskid_1", 
            })
            .set("Content-Type", "application/json");
    });

    test("case 5: user gives all required info, multiple tasks, viable route", async () => {
        
        //add new tasks
        //specific fields tbd
        var temp_taskid_1: ObjectId = new ObjectId(); 
        var temp_taskid_2: ObjectId = new ObjectId(); 
        var temp_taskid_3: ObjectId = new ObjectId(); 
        const task_1_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid_1, 
                name: "test_task_1",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");

        const task_2_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid_2, 
                name: "test_task_2",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");

        const task_3_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid_3, 
                name: "test_task_3",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");
        
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [task_1_res.body.new_task_id, task_2_res.body.new_task_id, task_3_res.body.new_task_id],
                userLocation: {
                    latitude: 49.269139,
                    longitude: -123.215108
                }, 
                userCurrTime: "10:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        // more expect here


        //clean up
        const cleanUpResponse_1 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: "temp_taskid_1", 
            })
            .set("Content-Type", "application/json");

        const cleanUpResponse_2 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: "temp_taskid_2", 
            })
            .set("Content-Type", "application/json");

        const cleanUpResponse_3 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: "temp_taskid_3", 
            })
            .set("Content-Type", "application/json");
    });

    test("case 6: user gives all required info, multiple tasks, no viable route", async () => {
        
        //add new tasks
        //specific fields tbd
        var temp_taskid_1: ObjectId = new ObjectId(); 
        var temp_taskid_2: ObjectId = new ObjectId(); 
        var temp_taskid_3: ObjectId = new ObjectId(); 
        const task_1_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid_1, 
                name: "test_task_1",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");

        const task_2_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid_2, 
                name: "test_task_2",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");

        const task_3_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid_3, 
                name: "test_task_3",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 45, 
                location_lng: 45, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");
        
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [task_1_res.body.new_task_id, task_2_res.body.new_task_id, task_3_res.body.new_task_id],
                userLocation: {
                    latitude: 49.269139,
                    longitude: -123.215108
                }, 
                userCurrTime: "10:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        // more expect here

        //clean up
        const cleanUpResponse_1 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: "temp_taskid_1", 
            })
            .set("Content-Type", "application/json");

        const cleanUpResponse_2 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: "temp_taskid_2", 
            })
            .set("Content-Type", "application/json");

        const cleanUpResponse_3 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: "temp_taskid_3", 
            })
            .set("Content-Type", "application/json");
    });

    
});