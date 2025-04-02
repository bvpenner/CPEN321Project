import request from "supertest";
import { app , db } from "../backend/ts-server/src/index";
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
        /*
        input: valid user ID
        expected status code: 200
        expected behavior: none
        expected output: get an array of all tasks from database
        */
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
        /*
        input: invalid user ID
        expected status code: 500
        expected behavior: none
        expected output: an error from database
        */
        const response = await request(app)
            .get("/getAllTasks")
            .query({ u_id: "" });

        expect(response.status).toBe(500); 
        expect(response.body).toHaveProperty("error");
    });
});

describe("/login (No Mocks)", () => {
    
    test("should return 200 for valid u_id", async () => {
        /*
        input: valid user ID, username, user email
        expected status code: 200
        expected behavior: add new user (if new)
        expected output: return new user id and whether is a new user 
        */
        const response = await request(app)
            .post("/login")
            .send({ u_id: validUID , name: valid_username, email: valid_useremail })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_user_id");
        expect(response.body).toHaveProperty("is_new");
        expect(typeof response.body.new_user_id).toBe("string");
        expect(typeof response.body.is_new).toBe("string");
    });
});

describe("/login (No Mocks)", () => {
    test("should return 500 for missing name", async () => {
        /*
        input: valid user ID, user email, invalid username
        expected status code: 500
        expected behavior: none
        expected output: an error from database 
        */
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
        /*
        input: valid user ID, username, invalid user email
        expected status code: 500
        expected behavior: none
        expected output: an error from database 
        */
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
        /*
        input: invalid user ID, valid username, user email
        expected status code: 500
        expected behavior: none
        expected output: an error from database 
        */
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
        /*
        input: task attributes listed below
        expected status code: 200
        expected behavior: add a task to database
        expected output: return a new task id
        */
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
        /*
        input: task attributes listed below
        expected status code: 200
        expected behavior: update a task
        expected output: return a new task id
        */
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
        /*
        input: valid user id and task id
        expected status code: 200
        expected behavior: delete a task
        expected output: none
        */
        const response = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: temp_taskid, 
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
    });
});

describe("/deleteTask (No Mocks)", () => {
    test("should return 500 for fail deleteTask (missing ID)", async () => {
        /*
        input: valid user id, but invalid task id
        expected status code: 500
        expected behavior: none
        expected output: an error from database
        */
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
        /*
        input: valid origin and destination coordinate
        expected status code: 200
        expected behavior: none
        expected output: a polygon
        */
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
    }, 60000);
});


describe("/fetchGeofences (No Mocks)", () => {
    test("should return 400 for incomplete request", async () => {
        /*
        input: invalid origin and destination coordinate
        expected status code: 400
        expected behavior: none
        expected output: an error
        */
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
        /*
        input: valid user location in lat, lng
               valid user time
               empty task list (selected)
        expected status code: 400
        expected behavior: none
        expected output: error msg, missing input infomation
        */       
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [],
                userLocation: {
                    latitude: 49.265819,
                    longitude: -123.249290
                }, 
                userCurrTime: "10:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(400);
    });

    test("case 2: user gives no time", async () => {
        /*
        input: valid user location in lat, lng
               invalid user time
               non empty task list
        expected status code: 400
        expected behavior: none
        expected output: error msg, missing input infomation
        */
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [1],
                userLocation: {
                    latitude: 49.265819,
                    longitude: -123.249290
                }, 
                userCurrTime: ""
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(400);
    });

    test("case 3: user gives no or invalid location", async () => {
        /*
        input: invalid user location in lat, lng
               valid user time
               task list of length 1
        expected status code: 400
        expected behavior: none
        expected output: error msg, missing input infomation
        */
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [1],
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
        /*
        input: valid user location in lat, lng
               valid user time
               task list of length 1
        expected status code: 200
        expected behavior: database is unchanged at the end
        expected output: a valid route contain one task & a time cost
        */
        const task_1_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
                name: "test_task_1",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 49.254830, 
                location_lng: -123.236329, 
                priority: 1, 
                description: ""
            })
            .set("Content-Type", "application/json");
        expect(task_1_res.status).toBe(200);
        expect(task_1_res.body).toHaveProperty("new_task_id");

        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [task_1_res.body.new_task_id],
                userLocation: {
                    latitude: 49.265819,
                    longitude: -123.249290
                }, 
                userCurrTime: "9:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("taskIds");
        expect(response.body).toHaveProperty("time_cost");
        expect(response.body.taskIds.length).toBe(1);
        expect(response.body.taskIds).toEqual([task_1_res.body.new_task_id]);

        // res.json({"taskIds": taskIds, "time_cost": result[1]});	
        // more expect


        //clean up
        const cleanUpResponse_1 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: task_1_res.body.new_task_id, 
            })
            .set("Content-Type", "application/json");
    });

    test("case 5: user gives all required info, multiple tasks, viable route", async () => {
        /*
        input: valid user location in lat, lng
               valid user time
               task list of length 3
        expected status code: 200
        expected behavior: database is unchanged at the end
        expected output: a valid sequence contain 3 tasks & a time cost
        */
        const task_1_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
                name: "test_task_1",
                start_time: "10:00", 
                end_time: "11:00", 
                duration: 30, 
                location_lat: 49.254830, 
                location_lng: -123.236329, 
                priority: 1, 
                description: "save on food"
            })
            .set("Content-Type", "application/json");

        const task_2_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
                name: "test_task_2",
                start_time: "10:00", 
                end_time: "12:00", 
                duration: 10, 
                location_lat: 49.231437, 
                location_lng: -123.155529, 
                priority: 1, 
                description: "cake shop"
            })
            .set("Content-Type", "application/json");

        const task_3_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
                name: "test_task_3",
                start_time: "10:00",    //OG 13:00
                end_time: "18:00", 
                duration: 40, 
                location_lat: 49.1748, 
                location_lng: -123.1311, 
                priority: 1, 
                description: "T&T supermarket"
            })
            .set("Content-Type", "application/json");
        
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [task_1_res.body.new_task_id, task_2_res.body.new_task_id, task_3_res.body.new_task_id],
                userLocation: {
                    latitude: 49.265819,
                    longitude: -123.249290
                }, 
                userCurrTime: "9:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("taskIds");
        expect(response.body).toHaveProperty("time_cost");
        expect(response.body.taskIds.length).toBe(3);
        expect(response.body.taskIds).toEqual([task_1_res.body.new_task_id, task_2_res.body.new_task_id, task_3_res.body.new_task_id]);


        //clean up
        const cleanUpResponse_1 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: task_1_res.body.new_task_id, 
            })
            .set("Content-Type", "application/json");

        const cleanUpResponse_2 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: task_2_res.body.new_task_id, 
            })
            .set("Content-Type", "application/json");

        const cleanUpResponse_3 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: task_3_res.body.new_task_id, 
            })
            .set("Content-Type", "application/json");
    });

    test("case 6: user gives all required info, multiple tasks, no viable route", async () => {
        /*
        input: valid user location in lat, lng
               valid user time
               task list of length 3
        expected status code: 200
        expected behavior: database is unchanged at the end
        expected output: an empty sequence due to no viable option and a time cost of -1
        */
        const task_1_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
                name: "test_task_1",
                start_time: "9:30", 
                end_time: "10:00", 
                duration: 30, 
                location_lat: 49.254830, 
                location_lng: -123.236329, 
                priority: 1, 
                description: "save on food"
            })
            .set("Content-Type", "application/json");

        const task_2_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
                name: "test_task_2",
                start_time: "10:00", 
                end_time: "12:00", 
                duration: 10, 
                location_lat: 49.231437, 
                location_lng: -123.155529, 
                priority: 1, 
                description: "cake shop"
            })
            .set("Content-Type", "application/json");

        const task_3_res = await request(app)
            .post("/addTask")
            .send({ 
                owner_id: validUID, 
                _id: "", 
                name: "test_task_3",
                start_time: "13:00", 
                end_time: "18:00", 
                duration: 40, 
                location_lat: 49.1748, 
                location_lng: -123.1311, 
                priority: 1, 
                description: "T&T supermarket"
            })
            .set("Content-Type", "application/json");
        
        const response = await request(app)
            .post("/fetchOptimalRoute")
            .send({ 
                allTasksID: [task_1_res.body.new_task_id, task_2_res.body.new_task_id, task_3_res.body.new_task_id],
                userLocation: {
                    latitude: 49.175211,
                    longitude: -123.130497
                }, 
                userCurrTime: "10:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("taskIds");
        expect(response.body).toHaveProperty("time_cost");
        expect(response.body.taskIds.length).toBe(0);
        expect(response.body.time_cost).toBe(-1);
        // more expect here

        //clean up
        const cleanUpResponse_1 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: task_2_res.body.new_task_id, 
            })
            .set("Content-Type", "application/json");

        const cleanUpResponse_2 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: task_1_res.body.new_task_id, 
            })
            .set("Content-Type", "application/json");

        const cleanUpResponse_3 = await request(app)
            .post("/deleteTask")
            .send({ 
                owner_id: validUID, 
                _id: task_3_res.body.new_task_id, 
            })
            .set("Content-Type", "application/json");
    });

});

test("case 7: user gives all required info, multiple tasks, multiple viable route", async () => {
    /*
    input: valid user location in lat, lng
           valid user time
           task list of length 3
    expected status code: 200
    expected behavior: database is unchanged at the end
    expected output: a valid sequence contain 3 tasks & a time cost
    */

    const task_1_res = await request(app)
        .post("/addTask")
        .send({ 
            owner_id: validUID, 
            _id: "", 
            name: "test_task_1",
            start_time: "10:00", 
            end_time: "12:00", 
            duration: 10, 
            location_lat: 49.231437, 
            location_lng: -123.155529, 
            priority: 1, 
            description: "cake shop"
        })
        .set("Content-Type", "application/json");
    
    const task_2_res = await request(app)
        .post("/addTask")
        .send({ 
            owner_id: validUID, 
            _id: "", 
            name: "test_task_2",
            start_time: "10:00", 
            end_time: "11:00", 
            duration: 30, 
            location_lat: 49.254830, 
            location_lng: -123.236329, 
            priority: 1, 
            description: "save on food"
        })
        .set("Content-Type", "application/json");

    const task_3_res = await request(app)
        .post("/addTask")
        .send({ 
            owner_id: validUID, 
            _id: "", 
            name: "test_task_3",
            start_time: "10:00",        //OG 13:00
            end_time: "18:00", 
            duration: 40, 
            location_lat: 49.1748, 
            location_lng: -123.1311, 
            priority: 1, 
            description: "T&T supermarket"
        })
        .set("Content-Type", "application/json");
    
    const response = await request(app)
        .post("/fetchOptimalRoute")
        .send({ 
            allTasksID: [task_1_res.body.new_task_id, task_2_res.body.new_task_id, task_3_res.body.new_task_id],
            userLocation: {
                latitude: 49.265819,
                longitude: -123.249290
            }, 
            userCurrTime: "9:00"
        })
        .set("Content-Type", "application/json");

    expect(response.status).toBe(200);
    expect(response.body).toHaveProperty("taskIds");
    expect(response.body).toHaveProperty("time_cost");
    expect(response.body.taskIds.length).toBe(3);
    expect(response.body.taskIds).toEqual([task_2_res.body.new_task_id, task_1_res.body.new_task_id, task_3_res.body.new_task_id]);


    //clean up
    const cleanUpResponse_1 = await request(app)
        .post("/deleteTask")
        .send({ 
            owner_id: validUID, 
            _id: task_1_res.body.new_task_id, 
        })
        .set("Content-Type", "application/json");

    const cleanUpResponse_2 = await request(app)
        .post("/deleteTask")
        .send({ 
            owner_id: validUID, 
            _id: task_2_res.body.new_task_id, 
        })
        .set("Content-Type", "application/json");

    const cleanUpResponse_3 = await request(app)
        .post("/deleteTask")
        .send({ 
            owner_id: validUID, 
            _id: task_3_res.body.new_task_id, 
        })
        .set("Content-Type", "application/json");
});
