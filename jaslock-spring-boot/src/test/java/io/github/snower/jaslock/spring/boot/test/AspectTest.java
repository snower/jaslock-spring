package io.github.snower.jaslock.spring.boot.test;

import io.github.snower.jaslock.spring.boot.AbstractBaseAspect;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AspectTest {
    @Test
    public void testCompileAndEvaluateKey() throws NoSuchMethodException {
        TestAspect testAspect = new TestAspect(null);

        TestClass t = new TestClass();
        Method m = t.getClass().getMethod("process", int.class, String.class, LocalDateTime.class, TestDto.class);
        Map<String, Object> map = new HashMap<>();
        map.put("name", 'a');
        map.put("age", 1);
        map.put("date", new Date());
        map.put("valueDto", new TestDto("a", 1, new ValueDto("b", 2, new ValueDto("c", 3, null, new HashMap<String, Object>() {{
            put("f", "f");
        }}), null)));
        Object[] args = new Object[]{1, "a", LocalDateTime.now(), new TestDto("a", 1, new ValueDto("b", 2,
                new ValueDto("c", 3, null, map), map))};

        Assert.assertEquals(AbstractBaseAspect.ConstKeyEvaluate.class, testAspect.compileKeyEvaluate(m, "").getClass());
        Assert.assertEquals(AbstractBaseAspect.ConstKeyEvaluate.class, testAspect.compileKeyEvaluate(m, "test").getClass());
        Assert.assertEquals(AbstractBaseAspect.ValueGetterKeyEvaluate.class, testAspect.compileKeyEvaluate(m, "{arg1}").getClass());
        Assert.assertEquals(AbstractBaseAspect.ValueGettersKeyEvaluate.class, testAspect.compileKeyEvaluate(m, "t_{arg1}").getClass());
        Assert.assertEquals(AbstractBaseAspect.ValueGettersKeyEvaluate.class, testAspect.compileKeyEvaluate(m, "{arg1}_t").getClass());
        Assert.assertEquals(AbstractBaseAspect.ValueGettersKeyEvaluate.class, testAspect.compileKeyEvaluate(m, "t_{arg1}_t").getClass());
        Assert.assertEquals(AbstractBaseAspect.SPELKeyEvaluate.class, testAspect.compileKeyEvaluate(m, "aaa_#{#p0}_#{#p1}_#{@testBean}").getClass());

        Assert.assertEquals("", testAspect.compileKeyEvaluate(m, "").evaluate(m, args, t));
        Assert.assertEquals("test", testAspect.compileKeyEvaluate(m, "test").evaluate(m, args, t));

        Assert.assertEquals("a", testAspect.compileKeyEvaluate(m, "{arg1}").evaluate(m, args, t));
        Assert.assertEquals("t_a", testAspect.compileKeyEvaluate(m, "t_{arg1}").evaluate(m, args, t));
        Assert.assertEquals("a_t", testAspect.compileKeyEvaluate(m, "{arg1}_t").evaluate(m, args, t));
        Assert.assertEquals("t_a_t", testAspect.compileKeyEvaluate(m, "t_{arg1}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}").evaluate(m, args, t));
        Assert.assertEquals("1_a_t", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_t", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_1", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_1", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_1_t", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_1_t", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_1", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.map.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_1", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.map.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_1_t", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.map.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_1_t", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.map.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_1", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.map.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_1", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.map.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_1_t", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.map.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_1_t", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.map.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{a0}_{a1}_{a3.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{a0}_{a1}_{a3.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{a0}_{a1}_{a3.age}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{a0}_{a1}_{a3.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{a0}_{a1}_{arg3.bge}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{a0}_{a1}_{arg3.bge}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{a0}_{a1}_{arg3.bge}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{a0}_{a1}_{arg3.bge}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_2", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_2", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_2_t", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_2_t", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_3", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_3", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_3_t", testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_3_t", testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{a.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{a.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{a.valueDto.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{a.valueDto.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.a.valueDto.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.a.valueDto.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.a.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.a.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.a.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.a.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.a.age}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.a.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.valueDto.a}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.valueDto.a}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0}_{arg1}_{arg3.valueDto.valueDto.a}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0}_{arg1}_{arg3.valueDto.valueDto.a}_t").evaluate(m, args, t));

        Assert.assertNotEquals("null", testAspect.compileKeyEvaluate(m, "{arg2}").evaluate(m, args, t));
        Assert.assertNotEquals("null", testAspect.compileKeyEvaluate(m, "{arg3.date}").evaluate(m, args, t));

    }

    @Test
    public void testCompileAndEvaluateKeyMap() throws NoSuchMethodException {
        TestAspect testAspect = new TestAspect(null);

        TestClass t = new TestClass();
        Method m = t.getClass().getMethod("process2", Map.class);
        Map<String, Object> map = new HashMap<>();
        map.put("name", 'a');
        map.put("age", 1);
        map.put("date", new Date());
        map.put("valueDto", new TestDto("a", 1, new ValueDto("b", 2,
                new ValueDto("c", 3, null, null), null)));
        Object[] args = new Object[]{map};

        Assert.assertEquals("a", testAspect.compileKeyEvaluate(m, "{arg0.name}").evaluate(m, args, t));
        Assert.assertEquals("t_a", testAspect.compileKeyEvaluate(m, "t_{arg0.name}").evaluate(m, args, t));
        Assert.assertEquals("a_t", testAspect.compileKeyEvaluate(m, "{arg0.name}_t").evaluate(m, args, t));
        Assert.assertEquals("t_a_t", testAspect.compileKeyEvaluate(m, "t_{arg0.name}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}").evaluate(m, args, t));
        Assert.assertEquals("1_a_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_1", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_1", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_1_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_1_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_null", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.map.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.map.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_null_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.map.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.map.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_null", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.map.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.map.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_null_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.map.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.map.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{a0}_{a1}_{a3.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{a0}_{a1}_{a3.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{a0}_{a1}_{a3.age}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{a0}_{a1}_{a3.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{a0}_{a1}_{arg0.valueDto.bge}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{a0}_{a1}_{arg0.valueDto.bge}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{a0}_{a1}_{arg0.valueDto.bge}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{a0}_{a1}_{arg0.valueDto.bge}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_2", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_2", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_2_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_2_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_3", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_3", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_3_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_3_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{a.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{a.valueDto.valueDto.age}").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{a.valueDto.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertThrows(IllegalArgumentException.class, () -> testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{a.valueDto.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_null", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.a.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.a.valueDto.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_null_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.a.valueDto.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.a.valueDto.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_null", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.a.age}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.a.age}").evaluate(m, args, t));
        Assert.assertEquals("1_a_null_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.a.age}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.a.age}_t").evaluate(m, args, t));

        Assert.assertEquals("1_a_null", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.valueDto.a}").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.valueDto.a}").evaluate(m, args, t));
        Assert.assertEquals("1_a_null_t", testAspect.compileKeyEvaluate(m, "{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.valueDto.a}_t").evaluate(m, args, t));
        Assert.assertEquals("t_1_a_null_t", testAspect.compileKeyEvaluate(m, "t_{arg0.age}_{arg0.name}_{arg0.valueDto.valueDto.valueDto.a}_t").evaluate(m, args, t));

        Assert.assertNotEquals("null", testAspect.compileKeyEvaluate(m, "{arg0.date}").evaluate(m, args, t));
        Assert.assertNotEquals("null", testAspect.compileKeyEvaluate(m, "{arg0.valueDto.date}").evaluate(m, args, t));
    }

    public static class TestAspect extends AbstractBaseAspect {

        protected TestAspect(SlockTemplate slockTemplate) {
            super(slockTemplate);
            this.spelExpressionParser = new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.MIXED,
                    getClass().getClassLoader()));

        }

        @Override
        public KeyEvaluate compileKeyEvaluate(Method method, String templateKey) {
            KeyEvaluate keyEvaluate = super.compileKeyEvaluate(method, templateKey);
            keyEvaluateCache.remove(method);
            return keyEvaluate;
        }
    }

    public static class TestClass {
        public String process(int a, String s, LocalDateTime d, TestDto t) {
            return a + s + d + t;
        }

        public Map<String, Object> process2(Map<String, Object> data) {
            return data;
        }
    }

    public static class TestDto {
        public String name;
        public int age;
        public Date date;
        public ValueDto valueDto;

        public TestDto(String name, int age, ValueDto valueDto) {
            this.name = name;
            this.age = age;
            this.date = new Date();
            this.valueDto = valueDto;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public Date getDate() {
            return date;
        }

        public ValueDto getValueDto() {
            return valueDto;
        }
    }

    public static class ValueDto {
        public String name;
        public int age;
        public ValueDto valueDto;
        public Map<String, Object> map;

        public ValueDto(String name, int age, ValueDto valueDto, Map<String, Object> map) {
            this.name = name;
            this.age = age;
            this.valueDto = valueDto;
            this.map = map;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public ValueDto getValueDto() {
            return valueDto;
        }

        public Map<String, Object> getMap() {
            return map;
        }
    }
}
