import express from 'express';
import { getAllTasks, deleteTask, addTask} from '../controllers/taskController';

const router = express.Router();

router.get('/getAllTasks', getAllTasks);
router.post('/addTask', addTask);
router.post('/deleteTask', deleteTask);

export default router;
