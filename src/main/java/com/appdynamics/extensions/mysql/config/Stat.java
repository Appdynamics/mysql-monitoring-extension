package com.appdynamics.extensions.mysql.config;

import javax.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class Stat {

    @XmlAttribute(name = "name")
    private String name;

    @XmlElement(name = "metric")
    private MetricConfig[] metric;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MetricConfig[] getMetric() {
        return metric;
    }

    public void setMetric(MetricConfig[] metric) {
        this.metric = metric;
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Stats{

        @XmlElement(name="stat")
        private Stat[] stat;

        public Stat[] getStat() {
            return stat;
        }

        public void setStat(Stat[] stat) {
            this.stat = stat;
        }
    }
}
