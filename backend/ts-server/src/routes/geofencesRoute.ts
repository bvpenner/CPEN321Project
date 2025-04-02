import express from 'express';
import { fetchGeofencesControl } from '../controllers/geofencesController';

const router = express.Router();

router.post('/fetchGeofences', fetchGeofencesControl);

export default router;
