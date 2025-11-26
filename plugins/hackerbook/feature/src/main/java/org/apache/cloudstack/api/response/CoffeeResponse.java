package org.apache.cloudstack.api.response;

import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;
import org.apache.cloudstack.api.Coffee;
import com.cloud.serializer.Param;

@EntityReference(value = Coffee.class)
public class CoffeeResponse extends BaseResponse {

    @SerializedName("id")
    @Param(description = "the coffee ID")
    private String id;

    @SerializedName("name")
    @Param(description = "the coffee name")
    private String name;

    @SerializedName("offering")
    @Param(description = "the type of coffee")
    private String offering;

    @SerializedName("size")
    @Param(description = "the size of coffee")
    private String size;

    @SerializedName("state")
    @Param(description = "current coffee state")
    private String state;

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setOffering(String offering) { this.offering = offering; }
    public void setSize(String size) { this.size = size; }
    public void setState(String state) { this.state = state; }
}