import request from "supertest";
import { app, db } from "../ts-server/src/index";
import * as dbService from "../database/mongodb-ts/userService";
import { isStringObject } from "util/types";
import { ObjectId } from "mongodb";
import { fetchGeofences } from "../ts-server/src/geofenceService";
import { fetchAllTaskRouteTime } from "../ts-server/src/fetchRouteService";
import { getTasksById } from "../database/mongodb-ts/userService";
import fetch from "node-fetch";
import { Client } from "@googlemaps/google-maps-services-js";
import fetchMock from 'fetch-mock-jest';
import type { FetchMockSandbox } from 'fetch-mock';

/////////////////////////////////////////////////
// With Mock
// npx jest backend_mock.test.ts --preset=ts-jest
/////////////////////////////////////////////////

jest.mock("../database/mongodb-ts/userService");
jest.mock('node-fetch', () => jest.fn());


class MockResponse {
    ok: boolean;
    statusText: string;
    constructor(public body: any, init: { status: number; statusText: string }) {
      this.ok = init.status < 400;
      this.statusText = init.statusText;
    }
    json() {
      return Promise.resolve(this.body);
    }
  }

  
jest.mock("../ts-server/src/geofenceService", () => {
    const actualModule = jest.requireActual("../ts-server/src/geofenceService");
    return {
        ...actualModule,
        fetchGeofences: jest.fn(),
    };
});

// jest.mock("node-fetch", () => {
//     const actualModule = jest.requireActual("node-fetch");
//     return {
//         ...actualModule,
//         fetch: jest.fn(),
//     };
// });

jest.mock("../ts-server/src/fetchRouteService", () => {
    const actualModule = jest.requireActual("../ts-server/src/fetchRouteService");
    return {
        ...actualModule,
        fetchAllTaskRouteTime: jest.fn(),
    };
});

const mockFetch = fetch as jest.Mock;

const googleMapsClientMock = {
    snapToRoads: jest.fn()
};
// jest.mock("@googlemaps/google-maps-services-js", () => ({
//     Client: jest.fn(() => ({
//         snapToRoads: jest.fn()
//     }))
// }));


const mockedDbService = dbService as jest.Mocked<typeof dbService>;
const validUID = "test_id_mocked";
const validUsername = "Mocked User";
const validUserEmail = "mockeduser@gmail.com";

const tempTaskId = "mocked_task_id";

describe("/getAllTasks (Mocked)", () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    test("should return mock task list", async () => {
        mockedDbService.getUserTasks.mockResolvedValue(["task1", "task2"]);

        const mock_tasklist_value = [
            {
                _id: "mock_task_1",
                name: "Mock Task 1",
                start: "10:00",
                end: "11:00",
                duration: 30,
                location_lat: 49.269139,
                location_lng: -123.215108,
                priority: 1,
                description: ""
            },
            {
                _id: "mock_task_2",
                name: "Mock Task 2",
                start: "11:00",
                end: "12:00",
                duration: 60,
                location_lat: 49.269140,
                location_lng: -123.215109,
                priority: 2,
                description: ""
            }
        ]
        mockedDbService.getAllTasksInList.mockResolvedValue(mock_tasklist_value);

        const response = await request(app)
            .get("/getAllTasks")
            .query({ u_id: validUID });

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("task_list");
        expect(response.body.task_list).toEqual(mock_tasklist_value);
    });
});

describe("/login (Mocked)", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        (fetchAllTaskRouteTime as jest.Mock).mockReset();
    });

    test("should return a mocked user", async () => {
        mockedDbService.addUser.mockResolvedValue([validUID, "0"]);

        const response = await request(app)
            .post("/login")
            .send({ u_id: validUID, name: validUsername, email: validUserEmail })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_user_id", validUID);
        expect(response.body).toHaveProperty("is_new", "0");
    });

    test("should create a new mocked user", async () => {
        mockedDbService.addUser.mockResolvedValue([validUID, "1"]);

        const response = await request(app)
            .post("/login")
            .send({ u_id: validUID, name: validUsername, email: validUserEmail })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_user_id", validUID);
        expect(response.body).toHaveProperty("is_new", "1");
    });
});

describe("/addTask (Mocked)", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        (fetchAllTaskRouteTime as jest.Mock).mockReset();
    });

    test("should return 200 for success create new task", async () => {
        mockedDbService.addTask.mockResolvedValue(tempTaskId);

        const response = await request(app)
            .post("/addTask")
            .send({
                owner_id: validUID,
                _id: "",
                name: "mock_task",
                start_time: "10:00",
                end_time: "11:00",
                duration: 30,
                location_lat: 45,
                location_lng: 45,
                priority: 1,
                description: "",
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_task_id", tempTaskId);
    });

    test("should return 200 for success update task", async () => {
        mockedDbService.modifyTask.mockResolvedValue(tempTaskId);

        const response = await request(app)
            .post("/addTask")
            .send({
                owner_id: validUID,
                _id: tempTaskId,
                name: "updated_mock_task",
                start_time: "10:00",
                end_time: "11:00",
                duration: 30,
                location_lat: 45,
                location_lng: 45,
                priority: 1,
                description: "",
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("new_task_id", tempTaskId);
    });
});

describe("/deleteTask (Mocked)", () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    test("should return 200 for success deleteTask", async () => {
        mockedDbService.deleteTaskById.mockResolvedValue();

        const response = await request(app)
            .post("/deleteTask")
            .send({ owner_id: validUID, _id: tempTaskId })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
    });

    test("should return 500 for fail deleteTask (missing ID)", async () => {
        const response = await request(app)
            .post("/deleteTask")
            .send({ owner_id: validUID, _id: "" })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error");
    });
});

describe("/fetchGeofences (Mocked)", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        (fetchGeofences as jest.Mock).mockReset();
        (fetchAllTaskRouteTime as jest.Mock).mockReset();
    });

    test("should return 200 for success response", async () => {
        (fetchGeofences as jest.Mock).mockResolvedValue({
            polygon: [
                { latitude: 49.269139, longitude: -123.215108 },
                { latitude: 49.269140, longitude: -123.215109 },
            ],
        });

        const response = await request(app)
            .post("/fetchGeofences")
            .send({
                origin: { latitude: 49.269139, longitude: -123.215108 },
                destination: { latitude: 49.26913, longitude: -123.215108 },
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty("polygon");
        expect(Array.isArray(response.body.polygon)).toBe(true);
    });

    test("should return 400 for incomplete request", async () => {
        const response = await request(app)
            .post("/fetchGeofences")
            .send({})
            .set("Content-Type", "application/json");

        expect(response.status).toBe(400);
        expect(response.body).toHaveProperty("error");
    });
});


describe("/fetchGeofences (Mocked)", () => {
    test("should return 500 when Google API does not respond", async () => {
        (fetchGeofences as jest.Mock).mockRejectedValue(new Error("Google API not responding"));

        const response = await request(app)
            .post("/fetchGeofences")
            .send({
                origin: { latitude: 49.269139, longitude: -123.215108 },
                destination: { latitude: 49.26913, longitude: -123.215108 },
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error", "Google API not responding");
    });
});

describe("/fetchGeofences (Mocked)", () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    test("should return 500 when Google Roads API does not respond", async () => {
        mockFetch.mockResolvedValue({
            ok: false,
            json: async () => ({
                routes: [
                    {
                        summary: "Mock Route",
                        overview_polyline: { points: "mockpolyline" },
                        legs: [
                            {
                                start_location: { lat: 49.269139, lng: -123.215108 },
                                end_location: { lat: 49.26913, lng: -123.215108 },
                                distance: { value: 1000 },
                                duration: { value: 600 },
                            }
                        ]
                    }
                ]
            }),
        });

        googleMapsClientMock.snapToRoads.mockRejectedValue(new Error("Google Roads API not responding"));

        const response = await request(app)
            .post("/fetchGeofences")
            .send({
                origin: { latitude: 49.269139, longitude: -123.215108 },
                destination: { latitude: 49.26913, longitude: -123.215108 },
            })
            .set("Content-Type", "application/json");

        console.log("Actual Response Body:", response.body);

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error");
    });
});

/////////////////////////////////////////////////
// FindOptimalRoute mocked tests 
/////////////////////////////////////////////////

describe("/fetchOptimalRoute", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockFetch.mockClear();
        mockFetch.mockRestore();
    });

    test("case 1: user gives all required info, but Google map distance matrix API failed", async () => {
        // status code OK means fine, otherwise failed
        // fetchAllTaskRouteTime.mockRejectedValue(new Error('Google Maps fail!'));     // this does not work
        //(fetchAllTaskRouteTime as jest.Mock).mockRejectedValue(new Error('Google Maps distance matrix fail!'));
        (fetch as jest.Mock).mockResolvedValue(
            new MockResponse(null, { status: 500, statusText: "Internal Server Error" })
          );

        //add new tasks
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

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error");


        //clean up
        const cleanUpResponse_1 = await request(app)
            .post("/deleteTask")
            .send({
                owner_id: validUID,
                _id: task_1_res.body.new_task_id,
            })
            .set("Content-Type", "application/json");
    });

    test("case 2: user gives all required info, but getTaskById fails???possible?", async () => {
        // status code OK means fine, otherwise failed
        // fetchAllTaskRouteTime.mockRejectedValue(new Error('Google Maps fail!'));     // this does not work
        (getTasksById as jest.Mock).mockRejectedValue(new Error(''));

        //add new tasks
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
                allTasksID: [-1],
                userLocation: {
                    latitude: 49.265819,
                    longitude: -123.249290
                },
                userCurrTime: "9:00"
            })
            .set("Content-Type", "application/json");

        expect(response.status).toBe(500);
        expect(response.body).toHaveProperty("error");

        //clean up
        const cleanUpResponse_1 = await request(app)
            .post("/deleteTask")
            .send({
                owner_id: validUID,
                _id: task_1_res.body.new_task_id,
            })
            .set("Content-Type", "application/json");
    });

    //another one for db disconnected?
});