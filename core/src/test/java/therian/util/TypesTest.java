package therian.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.reflect.TypeLiteral;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.junit.Assert;
import org.junit.Test;

import therian.Operation;
import therian.operation.Convert;
import therian.operation.Copy;
import therian.operation.Size;
import therian.operation.Transform;
import therian.operator.FromSourceToTarget.FromSource;
import therian.operator.FromSourceToTarget.ToTarget;
import therian.operator.convert.CopyingConverter;
import therian.operator.convert.IterableToIterator;
import therian.operator.size.SizeOfCollection;
import therian.operator.size.SizeOfIterable;
import therian.operator.size.SizeOfIterator;
import therian.testfixture.Book;
import therian.testfixture.MetasyntacticVariable;

public class TypesTest {

    @Test
    public void testGetSimpleType() {
        assertEquals(new TypeLiteral<ArrayList<String>>() {}.value, Types.resolveAt(
            Size.of(Positions.<ArrayList<String>> readWrite(new TypeLiteral<ArrayList<String>>() {})),
            Size.class.getTypeParameters()[0]));
    }

    @Test
    public void testGetInheritedType() {
        final Copy<Integer, String> copy =
            Copy.to(Positions.<String> readWrite(String.class), Positions.readOnly(Integer.valueOf(666)));
        assertEquals(Integer.class, Types.resolveAt(copy, Transform.class.getTypeParameters()[0]));
        assertEquals(String.class, Types.resolveAt(copy, Transform.class.getTypeParameters()[1]));
        assertEquals(Integer.class, Types.resolveAt(copy, Copy.class.getTypeParameters()[0]));
        assertEquals(String.class, Types.resolveAt(copy, Copy.class.getTypeParameters()[1]));
        final Convert<Integer, String> convert = Convert.to(String.class, Positions.readOnly(Integer.valueOf(666)));
        assertEquals(Integer.class, Types.resolveAt(convert, Transform.class.getTypeParameters()[0]));
        assertEquals(String.class, Types.resolveAt(convert, Transform.class.getTypeParameters()[1]));
        assertEquals(Integer.class, Types.resolveAt(convert, Convert.class.getTypeParameters()[0]));
        assertEquals(String.class, Types.resolveAt(convert, Convert.class.getTypeParameters()[1]));
    }

    @Test
    public void testGetInterfaceType() {
        final IterableToIterator iterableToIterator = new IterableToIterator();
        Type[] typeArguments = { TypeUtils.WILDCARD_ALL };

        assertTrue(TypeUtils.equals(TypeUtils.parameterize(Iterable.class, typeArguments),
            Types.resolveAt(iterableToIterator, FromSource.class.getTypeParameters()[0])));
        assertEquals(Iterator.class, Types.resolveAt(iterableToIterator, ToTarget.class.getTypeParameters()[0]));

        final CopyingConverter<Object, Book> forBook = CopyingConverter.forTargetType(Book.class);
        assertEquals(Object.class, Types.resolveAt(forBook, FromSource.class.getTypeParameters()[0]));
        assertEquals(Book.class, Types.resolveAt(forBook, ToTarget.class.getTypeParameters()[0]));

        assertEquals(Object.class,
            Types.resolveAt(CopyingConverter.IMPLEMENTING_COLLECTION, FromSource.class.getTypeParameters()[0]));
        assertEquals(Collection.class,
            Types.resolveAt(CopyingConverter.IMPLEMENTING_COLLECTION, ToTarget.class.getTypeParameters()[0]));
    }

    public void testNonTyped() {
        Assert.assertNull(Types.resolveAt(
            Copy.to(Positions.<String> readWrite(String.class), Positions.readOnly(Integer.valueOf(666))),
            Operation.class.getTypeParameters()[0]));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadTypeVariable() throws Exception {
        Types.resolveAt(Convert.to(String.class, Positions.readOnly(Integer.valueOf(666))), getClass()
            .getDeclaredMethod("foo").getTypeParameters()[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNotAssignableTypeVariable() {
        Types.resolveAt(Convert.to(String.class, Positions.readOnly(Integer.valueOf(666))),
            Copy.class.getTypeParameters()[0]);
    }

    @Test
    public void testMatchesOperator() {
        final Size<List<MetasyntacticVariable>> size =
            Size.of(Positions.<List<MetasyntacticVariable>> readOnly(Arrays.asList(MetasyntacticVariable.values())));
        assertTrue(size.matches(new SizeOfCollection()));
        assertTrue(size.matches(new SizeOfIterable()));
        assertFalse(size.matches(new SizeOfIterator()));
    }

    @Test
    public void testNarrowestParameterizedType() {
        final ParameterizedType listOfStringType = TypeUtils.parameterize(List.class, String.class);

        assertEquals(listOfStringType, Types.narrowestParameterizedType(null, listOfStringType));

        final List<String> arrayAsList = Arrays.asList("foo", "bar", "baz");
        @SuppressWarnings("rawtypes")
        final Class<? extends List> arrayAsListType = arrayAsList.getClass();
        assertEquals(TypeUtils.parameterize(arrayAsListType, String.class),
            Types.narrowestParameterizedType(arrayAsListType, listOfStringType));

        assertEquals(TypeUtils.parameterize(ArrayList.class, String.class),
            Types.narrowestParameterizedType(ArrayList.class, listOfStringType));

        @SuppressWarnings("rawtypes")
        final Class<? extends List> arrayListSubListType = new ArrayList<String>(arrayAsList).subList(0, 1).getClass();
        assertEquals(TypeUtils.parameterize(AbstractList.class, String.class),
            Types.narrowestParameterizedType(arrayListSubListType, listOfStringType));
    }

    @SuppressWarnings("unused")
    private <T> void foo() {

    }
}
