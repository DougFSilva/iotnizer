package com.dougfsilva.iotnizer.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.dougfsilva.iotnizer.config.mongo.MeasuredValueCodec;
import com.dougfsilva.iotnizer.config.mongo.MeasuringDeviceCodec;
import com.dougfsilva.iotnizer.config.mongo.MongoConnection;
import com.dougfsilva.iotnizer.model.MeasuredValue;
import com.dougfsilva.iotnizer.model.MeasuringDevice;
import com.dougfsilva.iotnizer.model.User;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;

@Repository
public class MeasuringDeviceRepository {

	@Value("${mongodb.database}")
	private String database;

	private final MongoConnection connection;

	public MeasuringDeviceRepository(MongoConnection connection) {
		this.connection = connection;
	}

	public String create(MeasuringDevice device) {
		InsertOneResult insertOneResult = getCollection().insertOne(device);
		connection.close();
		return insertOneResult.getInsertedId().asObjectId().getValue().toHexString();
	}

	public void delete(MeasuringDevice device) {
		getCollection().deleteOne(Filters.eq(new ObjectId(device.getId())));
		connection.close();
	}

	public MeasuringDevice update(MeasuringDevice device) {
		MeasuringDevice updatedDevice = (getCollection().findOneAndUpdate(
				Filters.eq(new ObjectId(device.getId())),
				Updates.combine(Updates.set("tag", device.getTag()), 
						Updates.set("location", device.getLocation()),
						Updates.set("mqttTopic", device.getMqttTopic())),
				new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)));
		connection.close();
		return updatedDevice;
	}
	
	public void addValues(User user, String device_id, List<MeasuredValue> value) {
		getCollection().updateOne(Filters.and(Filters.eq(new ObjectId(device_id)), Filters.eq("user_id",user.getId())), 
				Updates.pushEach("values", value, new PushOptions().slice(-12000)));
		connection.close();
	}
	
	public void removeValue(MeasuringDevice device, String value_id) {
		getCollection().updateMany(Filters.eq(new ObjectId(device.getId())), 
				Updates.pullByFilter(Filters.eq("values", new Document().append("_id", new ObjectId(value_id)))));
		connection.close();
	}
	
	public void removeValueByTimestamp(MeasuringDevice device, LocalDateTime initialTimestamp, LocalDateTime finalTimestamp) {
		getCollection().updateMany(Filters.eq(new ObjectId(device.getId())), 
				Document.parse(String.format(
						"{$pull : {values: {$and: [{timestamp: {$gte:ISODate('%s')}}, {timestamp: {$lte:ISODate('%s')}}]}}}", 
						initialTimestamp.toString() + "Z", finalTimestamp.toString() + "Z")));
		
		connection.close();
	}
	
	public Optional<MeasuringDevice> findById(String id) {
		Optional<MeasuringDevice> device = Optional
				.ofNullable(getCollection().find(Filters.eq(new ObjectId(id)), MeasuringDevice.class).first());
		connection.close();
		return device;
	}

	public List<MeasuringDevice> findAllByUser(User user) {
		List<MeasuringDevice> devices = new ArrayList<>();
		MongoCursor<MeasuringDevice> mongoCursor = getCollection().find(Filters.eq("user_id", user.getId()))
				.batchSize(10000).iterator();
		mongoCursor.forEachRemaining(cursor -> devices.add(cursor));
		connection.close();
		return devices;
	}
	
	public Optional<MeasuringDevice> findByIdAndfilterValuesByTimestamp(String id, LocalDateTime initialTimestamp, LocalDateTime finalTimestamp){
		Optional<MeasuringDevice> device = Optional.ofNullable(getCollection().aggregate(java.util.Arrays.asList(Aggregates.match(Filters.eq(new ObjectId(id))), 
				Document.parse(String.format("{$project: {user_id:1, tag:1, location:1, mqttTopic:1, values:{$filter:{input:'$values',as:'value', cond: {$and :["
						+ "{$gte:['$$value.timestamp', ISODate('%s')]},"
						+ "{$lte:['$$value.timestamp', ISODate('%s')]}]}}}}}", 
						initialTimestamp.toString() + "Z", finalTimestamp.toString() + "Z")))).first());
		connection.close();
		return device;
	}

	public List<MeasuringDevice> findAll() {
		List<MeasuringDevice> devices = new ArrayList<>();
		MongoCursor<MeasuringDevice> mongoCursor = getCollection().find().batchSize(10000).iterator();
		mongoCursor.forEachRemaining(cursor -> devices.add(cursor));
		connection.close();
		return devices;
	}

	private MongoCollection<MeasuringDevice> getCollection() {
		return connection.connect(new MeasuringDeviceCodec(), new MeasuredValueCodec()).getClient()
				.getDatabase(database)
				.getCollection("measuring_device", MeasuringDevice.class);
	}

}