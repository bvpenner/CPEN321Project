import request from 'supertest';
import express, { Request, Response } from 'express';
import * as dbService from '../../database/mongodb-ts/userService';

// --------------------------------------------------------------------------
// Mock the database connection
// --------------------------------------------------------------------------
jest.mock('../../database/mongodb-ts/database', () => ({
  connectDB: jest.fn().mockReturnValue({
    collection: jest.fn().mockReturnValue({
      insertOne: jest.fn().mockResolvedValue({ insertedId: 'new_task_123' }),
      findOne: jest.fn().mockImplementation((query) => {
        if (query._id === 'task123') {
          return Promise.resolve({
            _id: 'task123',
            name: 'Test Task',
            start_time: '10:00',
            end_time: '11:00',
            duration: 60,
            location_lat: 49.2827,
            location_lng: -123.1207,
            priority: 1,
            description: 'Test task description'
          });
        }
        return Promise.resolve(null);
      }),
      updateOne: jest.fn().mockResolvedValue({ modifiedCount: 1 }),
      deleteOne: jest.fn().mockResolvedValue({ deletedCount: 1 }),
      find: jest.fn().mockReturnValue({
        toArray: jest.fn().mockResolvedValue([{
          _id: 'task123',
          name: 'Test Task',
          start_time: '10:00',
          end_time: '11:00',
          duration: 60,
          location_lat: 49.2827,
          location_lng: -123.1207,
          priority: 1,
          description: 'Test task description'
        }])
      })
    })
  }),
  client: {
    db: jest.fn().mockReturnValue({
      collection: jest.fn().mockReturnValue({
        insertOne: jest.fn().mockResolvedValue({ insertedId: 'new_task_123' }),
        findOne: jest.fn().mockImplementation((query) => {
          if (query._id === 'task123') {
            return Promise.resolve({
              _id: 'task123',
              name: 'Test Task',
              start_time: '10:00',
              end_time: '11:00',
              duration: 60,
              location_lat: 49.2827,
              location_lng: -123.1207,
              priority: 1,
              description: 'Test task description'
            });
          }
          return Promise.resolve(null);
        }),
        updateOne: jest.fn().mockResolvedValue({ modifiedCount: 1 }),
        deleteOne: jest.fn().mockResolvedValue({ deletedCount: 1 }),
        find: jest.fn().mockReturnValue({
          toArray: jest.fn().mockResolvedValue([{
            _id: 'task123',
            name: 'Test Task',
            start_time: '10:00',
            end_time: '11:00',
            duration: 60,
            location_lat: 49.2827,
            location_lng: -123.1207,
            priority: 1,
            description: 'Test task description'
          }])
        })
      })
    })
  }
}));

// --------------------------------------------------------------------------
// Mock the database service functions
// --------------------------------------------------------------------------
jest.mock('../../database/mongodb-ts/userService', () => ({
  ...jest.requireActual('../../database/mongodb-ts/userService'),
  addTask: jest.fn().mockResolvedValue('new_task_123'),
  modifyTask: jest.fn().mockResolvedValue('task123'),
  addTaskToUser: jest.fn().mockResolvedValue(true),
  getUserTasks: jest.fn().mockResolvedValue(['task123']),
  getAllTasksInList: jest.fn().mockResolvedValue([{
    _id: 'task123',
    name: 'Test Task',
    start_time: '10:00',
    end_time: '11:00',
    duration: 60,
    location_lat: 49.2827,
    location_lng: -123.1207,
    priority: 1,
    description: 'Test task description'
  }]),
  deleteTaskById: jest.fn().mockResolvedValue('task123')
}));

// --------------------------------------------------------------------------
// Create Express App and Route Handlers
// --------------------------------------------------------------------------
const app = express();
app.use(express.json());

// POST /addTask – handles both task creation and modification
app.post('/addTask', async (req: Request, res: Response) => {
  try {
    const {
      owner_id, _id, name, start_time,
      end_time, duration, location_lat,
      location_lng, priority, description
    } = req.body;

    if (!_id) {
      // Create a new task
      const newTaskId = await dbService.addTask(name, start_time, end_time, duration, location_lat, location_lng, priority, description);
      await dbService.addTaskToUser(owner_id, newTaskId);
      return res.status(200).json({ new_task_id: newTaskId });
    } else {
      // Modify existing task
      const modifiedTaskId = await dbService.modifyTask(_id, name, start_time, end_time, duration, location_lat, location_lng, priority, description);
      if (modifiedTaskId === _id) {
        return res.status(200).json({ new_task_id: modifiedTaskId });
      } else {
        return res.status(500).json({ error: "modifyTask failed, check server log" });
      }
    }
  } catch (error: any) {
    console.error(error);
    res.status(500).json({ error: error.message });
  }
});

// GET /getAllTasks – retrieves all tasks for a given user
app.get('/getAllTasks', async (req: Request, res: Response) => {
  try {
    const { u_id } = req.query;
    if (!u_id || typeof u_id !== 'string') {
      throw new Error("Missing or invalid u_id in request query");
    }
    const taskIdList = await dbService.getUserTasks(u_id);
    const taskList = await dbService.getAllTasksInList(taskIdList);
    res.status(200).json({ task_list: taskList || [] });
  } catch (error: any) {
    console.error(error);
    res.status(500).json({ error: error.message });
  }
});

// POST /deleteTask – deletes a task by its ID
app.post('/deleteTask', async (req: Request, res: Response) => {
  try {
    const { owner_id, _id } = req.body;
    if (!_id) {
      throw new Error("Missing or invalid _id in request body");
    }
    const deletedTaskId = await dbService.deleteTaskById(owner_id, _id);
    res.status(200).json({ new_task_id: deletedTaskId });
  } catch (error: any) {
    console.error(error);
    res.status(500).json({ error: error.message });
  }
});

// --------------------------------------------------------------------------
// Test Cases
// --------------------------------------------------------------------------

describe("POST /addTask Endpoint", () => {
  describe("Unmocked Behavior", () => {
    test("Add New Task", async () => {
      const taskData = {
        owner_id: "user123",
        name: "Test Task",
        start_time: "10:00",
        end_time: "11:00",
        duration: 60,
        location_lat: 49.2827,
        location_lng: -123.1207,
        priority: 1,
        description: "Test task description"
      };

      const response = await request(app).post('/addTask').send(taskData);
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('new_task_id');
    });

    test("Modify Existing Task", async () => {
      const taskData = {
        owner_id: "user123",
        _id: "task123",
        name: "Modified Task",
        start_time: "10:00",
        end_time: "11:00",
        duration: 60,
        location_lat: 49.2827,
        location_lng: -123.1207,
        priority: 1,
        description: "Modified task description"
      };

      const response = await request(app).post('/addTask').send(taskData);
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('new_task_id');
      expect(response.body.new_task_id).toBe('task123');
    });
  });

  describe("Mocked Behavior", () => {
    test("Database Error on Task Creation", async () => {
      const spyAddTask = jest.spyOn(dbService, 'addTask')
        .mockImplementation(() => { throw new Error('Database error'); });

      const taskData = {
        owner_id: "user123",
        name: "Test Task",
        start_time: "10:00",
        end_time: "11:00",
        duration: 60,
        location_lat: 49.2827,
        location_lng: -123.1207,
        priority: 1,
        description: "Test task description"
      };

      const response = await request(app).post('/addTask').send(taskData);
      expect(response.status).toBe(500);
      expect(response.body).toHaveProperty('error');

      spyAddTask.mockRestore();
    });
  });
});

describe("GET /getAllTasks Endpoint", () => {
  describe("Unmocked Behavior", () => {
    test("Get User Tasks", async () => {
      const response = await request(app)
        .get('/getAllTasks')
        .query({ u_id: 'user123' });
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('task_list');
      expect(Array.isArray(response.body.task_list)).toBe(true);
    });

    test("Missing User ID", async () => {
      const response = await request(app).get('/getAllTasks');
      expect(response.status).toBe(500);
      expect(response.body).toHaveProperty('error');
    });
  });

  describe("Mocked Behavior", () => {
    test("Empty Task List", async () => {
      const spyGetUserTasks = jest.spyOn(dbService, 'getUserTasks').mockResolvedValue([]);
      const spyGetAllTasksInList = jest.spyOn(dbService, 'getAllTasksInList').mockResolvedValue([]);

      const response = await request(app)
        .get('/getAllTasks')
        .query({ u_id: 'user123' });
      expect(response.status).toBe(200);
      expect(response.body.task_list).toEqual([]);

      spyGetUserTasks.mockRestore();
      spyGetAllTasksInList.mockRestore();
    });

    test("Database Error", async () => {
      const spyGetUserTasks = jest.spyOn(dbService, 'getUserTasks').mockImplementation(() => { throw new Error('Database connection failed'); });
      const response = await request(app)
        .get('/getAllTasks')
        .query({ u_id: 'user123' });
      expect(response.status).toBe(500);
      expect(response.body).toHaveProperty('error');
      spyGetUserTasks.mockRestore();
    });
  });
});

describe("POST /deleteTask Endpoint", () => {
  describe("Unmocked Behavior", () => {
    test("Delete Existing Task", async () => {
      const deleteData = {
        owner_id: "user123",
        _id: "task123"
      };

      const response = await request(app).post('/deleteTask').send(deleteData);
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('new_task_id');
    });

    test("Missing Task ID", async () => {
      const deleteData = { owner_id: "user123" };
      const response = await request(app).post('/deleteTask').send(deleteData);
      expect(response.status).toBe(500);
      expect(response.body).toHaveProperty('error');
    });
  });

  describe("Mocked Behavior", () => {
    test("Database Error", async () => {
      const spyDeleteTask = jest.spyOn(dbService, 'deleteTaskById')
        .mockImplementation(() => { throw new Error('Database error'); });

      const deleteData = {
        owner_id: "user123",
        _id: "task123"
      };

      const response = await request(app).post('/deleteTask').send(deleteData);
      expect(response.status).toBe(500);
      expect(response.body).toHaveProperty('error');

      spyDeleteTask.mockRestore();
    });
  });
});
