import { Request, Response } from 'express';
import { RouteRequestBody } from '../index';
import { fetchGeofences } from '../geofenceService';

export const fetchGeofencesControl = async (req: Request<{}, any, RouteRequestBody>, res: Response): Promise<void> => {
    try {
        const { origin, destination } = req.body;
        if (!origin || !destination) {
            res.status(400).json({ error: 'Missing origin or destination coordinates.' });
            return;
        }
        const result = await fetchGeofences(origin, destination);
        console.log("fetchGeofences result: " + result)
        res.json(result);
    } catch (error: any) {
        // console.error(error);
        res.status(500).json({ error: error.message });
    }
};