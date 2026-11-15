with open('C:/Users/kakal/Documents/PowerLevelling/src/main/java/com/powerlevelling/client/render/AuraLayer.java', 'r') as f:
    c = f.read()
c = c.replace('public class HairLayer', 'public class AuraLayer')
c = c.replace('HairLayer(', 'AuraLayer(')
with open('C:/Users/kakal/Documents/PowerLevelling/src/main/java/com/powerlevelling/client/render/AuraLayer.java', 'w') as f:
    f.write(c)