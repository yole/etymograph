export default function FormRow(props) {
    const register = props.register
    return <tr>
        <td><label>{props.label}</label></td>
        <td><input type="text" {...register(props.id)}/></td>
    </tr>
}
