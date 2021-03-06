SELECT DISTINCT "r_coll_main"."coll_id", "r_coll_main"."parent_coll_name",
  "r_coll_main"."coll_name", "r_coll_main"."coll_owner_name",
  "r_coll_main"."coll_owner_zone", "r_meta_main"."meta_attr_name",
  "r_meta_main"."meta_attr_value", "r_meta_main"."meta_attr_unit",
  "r_user_main"."user_name", "r_user_main"."zone_name", "r_objt_access"."access_type_id"
  FROM
       "r_coll_main" JOIN "r_objt_metamap" ON "r_coll_main"."coll_id" = "r_objt_metamap"."object_id" JOIN "r_meta_main" ON "r_objt_metamap"."meta_id" = "r_meta_main"."meta_id" JOIN "r_objt_access" ON "r_coll_main"."coll_id" = "r_objt_access"."object_id" JOIN "r_user_main" ON "r_objt_access"."user_id" = "r_user_main"."user_id"
  WHERE "r_meta_main"."meta_attr_unit" = 'iRODSUserTagging:Share'
    AND "r_user_main"."user_name" = 'test2' AND "r_user_main"."zone_name" = 'test1'
    AND "r_coll_main"."coll_owner_name" <> 'test2'
  ORDER BY "r_coll_main"."parent_coll_name" ASC, "r_coll_main"."coll_name" ASC
