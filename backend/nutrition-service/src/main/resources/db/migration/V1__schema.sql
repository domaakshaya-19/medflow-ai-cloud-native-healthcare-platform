CREATE TABLE foods(id INTEGER PRIMARY KEY,name VARCHAR(250) NOT NULL,kcal NUMERIC(8,2) NOT NULL,protein NUMERIC(8,2) NOT NULL,carbohydrate NUMERIC(8,2) NOT NULL,fat NUMERIC(8,2) NOT NULL,source VARCHAR(250) NOT NULL);
INSERT INTO foods VALUES(171688,'Apples, raw, with skin',52.00,0.26,13.81,0.17,'https://fdc.nal.usda.gov/food-details/171688/nutrients');
INSERT INTO foods VALUES(173944,'Bananas, raw',89.00,1.09,22.84,0.33,'https://fdc.nal.usda.gov/food-details/173944/nutrients');
CREATE TABLE nutrition_profiles(owner_user_id UUID PRIMARY KEY,goal VARCHAR(30) NOT NULL,notes VARCHAR(500),updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE wellness_progress(id UUID PRIMARY KEY,owner_user_id UUID NOT NULL,entry_date DATE NOT NULL,fruit_servings INTEGER NOT NULL CHECK(fruit_servings BETWEEN 0 AND 20),notes VARCHAR(500),created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(owner_user_id,entry_date));
